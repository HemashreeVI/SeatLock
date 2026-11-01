package com.HemashreeVI.seatlock.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes outbox rows to Kafka. Delivery is at-least-once: if we crash after sending but
 * before marking the row published, it is sent again, so every consumer is idempotent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelay {

    public static final String HEADER_EVENT_ID = "eventId";
    public static final String HEADER_EVENT_TYPE = "eventType";

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;

    @Transactional
    public int publishBatch(int limit) {
        List<OutboxEvent> batch = repository.lockUnpublished(limit);
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(event.getTopic(), event.getAggregateId(), event.getPayload());
                record.headers().add(HEADER_EVENT_ID, String.valueOf(event.getId()).getBytes(StandardCharsets.UTF_8));
                record.headers().add(HEADER_EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8));
                kafka.send(record).get(10, TimeUnit.SECONDS);
                event.setPublishedAt(Instant.now());
                published++;
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                event.setAttempts(event.getAttempts() + 1);
                String message = String.valueOf(e.getMessage());
                event.setLastError(message.substring(0, Math.min(500, message.length())));
                log.warn("Outbox publish failed for event {} (attempt {}): {}", event.getId(), event.getAttempts(), message);
                break; // keep ordering: don't publish later events before this one
            }
        }
        return published;
    }
}
