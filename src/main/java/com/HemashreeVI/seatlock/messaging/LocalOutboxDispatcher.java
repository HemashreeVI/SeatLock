package com.HemashreeVI.seatlock.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broker-less replacement for the Kafka relay (seatlock.messaging.mode=local), used for free demo
 * deployments where no Kafka is available.
 * <p>
 * The outbox pattern is unchanged: business code still writes messages to outbox_events in its
 * own transaction. This dispatcher reads unpublished rows in id order and calls the matching
 * {@link MessageHandler} directly. A row is marked published only AFTER its handler succeeded,
 * so delivery stays at-least-once and survives restarts. A handler runs outside the
 * dispatcher's own transaction, exactly as it would when called by Kafka.
 * <p>
 * Failures are retried with a growing delay; after {@value #MAX_ATTEMPTS} attempts the row is
 * parked (marked published with a DEAD-LETTER note), the local equivalent of a dead-letter topic.
 * Stuck payments are then picked up by the payment reconciler, as in Kafka mode.
 * Designed for a single instance (several instances would deliver some messages twice, which the
 * idempotent handlers tolerate, but there is no partition ordering).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = MessagingMode.PROPERTY, havingValue = MessagingMode.LOCAL)
public class LocalOutboxDispatcher {

    static final int MAX_ATTEMPTS = 4;

    private final OutboxRepository repository;
    private final Map<String, MessageHandler> handlers = new HashMap<>();
    private final TransactionTemplate tx;
    private final Map<Long, Instant> retryNotBefore = new ConcurrentHashMap<>();

    public LocalOutboxDispatcher(OutboxRepository repository, List<MessageHandler> handlerBeans,
                                 PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.tx = new TransactionTemplate(transactionManager);
        handlerBeans.forEach(h -> handlers.put(h.topic(), h));
        log.info("Local (broker-less) messaging: delivering outbox messages in-process to {}", handlers.keySet());
    }

    /** Delivers up to {@code limit} pending messages. Returns how many were delivered. */
    public int dispatchBatch(int limit) {
        int delivered = 0;
        for (Long id : repository.findUnpublishedIds(limit)) {
            Instant notBefore = retryNotBefore.get(id);
            if (notBefore != null && Instant.now().isBefore(notBefore)) {
                continue;
            }
            OutboxEvent event = repository.findById(id).orElse(null);
            if (event == null || event.getPublishedAt() != null) {
                continue;
            }
            MessageHandler handler = handlers.get(event.getTopic());
            if (handler == null) {
                markPublished(id, "DEAD-LETTER: no handler for topic " + event.getTopic());
                continue;
            }
            try {
                handler.handle(event.getPayload(), String.valueOf(id));
                markPublished(id, null);
                retryNotBefore.remove(id);
                delivered++;
            } catch (RuntimeException e) {
                recordFailure(event, e);
            }
        }
        return delivered;
    }

    private void recordFailure(OutboxEvent event, RuntimeException e) {
        int attempts = event.getAttempts() + 1;
        String message = String.valueOf(e.getMessage());
        if (attempts >= MAX_ATTEMPTS) {
            log.error("Message {} ({}) failed {} times, parking it: {}", event.getId(), event.getEventType(), attempts, message);
            markPublished(event.getId(), "DEAD-LETTER: " + message);
            retryNotBefore.remove(event.getId());
            return;
        }
        log.warn("Message {} ({}) failed (attempt {}), will retry: {}", event.getId(), event.getEventType(), attempts, message);
        retryNotBefore.put(event.getId(), Instant.now().plusSeconds(attempts));   // 1s, 2s, 3s
        tx.executeWithoutResult(s -> repository.findById(event.getId()).ifPresent(row -> {
            row.setAttempts(attempts);
            row.setLastError(truncate(message));
        }));
    }

    private void markPublished(Long id, String note) {
        tx.executeWithoutResult(s -> repository.findById(id).ifPresent(row -> {
            row.setPublishedAt(Instant.now());
            if (note != null) {
                row.setLastError(truncate(note));
            }
        }));
    }

    private static String truncate(String s) {
        return s.substring(0, Math.min(500, s.length()));
    }
}
