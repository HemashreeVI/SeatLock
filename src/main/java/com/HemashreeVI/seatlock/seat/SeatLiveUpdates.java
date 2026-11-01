package com.HemashreeVI.seatlock.seat;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pushes seat changes to browsers in real time (Server-Sent Events), so a seat turns amber or red
 * on everyone's screen within a fraction of a second of being held or booked.
 * <p>
 * Changes are coalesced: a committed transaction only marks its event "dirty", and a flush every
 * few hundred milliseconds sends each dirty event's current seat statuses once, however many
 * bookings happened in between. That keeps the cost bounded during a rush (one small query per
 * event per flush, not one per booking per viewer).
 * <p>
 * Subscribers live in this instance's memory: fine for the single-instance demo. Several
 * instances would need to fan changes out to each other (for example with Redis pub/sub).
 */
@Slf4j
@Component
public class SeatLiveUpdates {

    private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final SeatRepository seatRepository;
    private final int maxSubscribers;
    private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final Set<Long> dirty = ConcurrentHashMap.newKeySet();
    private final AtomicInteger count = new AtomicInteger();

    public SeatLiveUpdates(SeatRepository seatRepository,
                           @Value("${seatlock.live.max-subscribers:1000}") int maxSubscribers) {
        this.seatRepository = seatRepository;
        this.maxSubscribers = maxSubscribers;
    }

    /** Opens a stream for one event and immediately sends the current statuses. */
    public SseEmitter subscribe(Long eventId) {
        if (count.incrementAndGet() > maxSubscribers) {
            count.decrementAndGet();
            throw new TooManySubscribersException();
        }
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        subscribers.computeIfAbsent(eventId, id -> new CopyOnWriteArraySet<>()).add(emitter);
        Runnable remove = () -> remove(eventId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(eventId, emitter, seatRepository.findStatuses(eventId));
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatsChanged(SeatsChangedEvent event) {
        if (subscribers.containsKey(event.eventId())) {
            dirty.add(event.eventId());
        }
    }

    @Scheduled(fixedDelayString = "${seatlock.live.flush-interval-ms:300}")
    public void flush() {
        for (Long eventId : List.copyOf(dirty)) {
            dirty.remove(eventId);
            Set<SseEmitter> emitters = subscribers.get(eventId);
            if (emitters == null || emitters.isEmpty()) {
                continue;
            }
            try {
                List<SeatStatusUpdate> statuses = seatRepository.findStatuses(eventId);
                emitters.forEach(emitter -> send(eventId, emitter, statuses));
            } catch (RuntimeException e) {
                dirty.add(eventId);   // try again on the next flush
                log.warn("Live seat update for event {} failed: {}", eventId, e.getMessage());
            }
        }
    }

    /** Keeps idle connections open through proxies that close silent streams. */
    @Scheduled(fixedDelayString = "${seatlock.live.heartbeat-interval-ms:20000}")
    public void heartbeat() {
        subscribers.forEach((eventId, emitters) -> emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException e) {
                remove(eventId, emitter);
            }
        }));
    }

    /** Close open streams on shutdown so a graceful shutdown or redeploy doesn't wait on them. */
    @PreDestroy
    public void closeAll() {
        subscribers.values().forEach(emitters -> emitters.forEach(SseEmitter::complete));
        subscribers.clear();
    }

    public int subscriberCount() {
        return count.get();
    }

    private void send(Long eventId, SseEmitter emitter, List<SeatStatusUpdate> statuses) {
        try {
            emitter.send(SseEmitter.event().name("seats").data(statuses, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            remove(eventId, emitter);   // the browser went away
        }
    }

    private void remove(Long eventId, SseEmitter emitter) {
        Set<SseEmitter> emitters = subscribers.get(eventId);
        if (emitters != null && emitters.remove(emitter)) {
            count.decrementAndGet();
            if (emitters.isEmpty()) {
                subscribers.remove(eventId, emitters);
            }
        }
    }

    /** Protects the server from unbounded open connections (HTTP 503). */
    public static class TooManySubscribersException extends RuntimeException {
        public TooManySubscribersException() {
            super("Too many live viewers right now; the page falls back to refreshing periodically");
        }
    }
}
