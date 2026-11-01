package com.HemashreeVI.seatlock.seat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Read-through cache of an event's seat map in Redis.
 * <p>
 * Browsing is by far the most frequent request during a sale, so serving it from Redis keeps
 * that load off Postgres. The cache is ONLY for display: holding a seat always checks the
 * database, so a slightly stale map can never cause a double-booking.
 * <ul>
 *   <li>Entries are evicted right after any transaction that changes seats commits.</li>
 *   <li>A short TTL is the safety net for any eviction that is missed.</li>
 *   <li>If Redis is down we simply fall back to the database.</li>
 * </ul>
 */
@Slf4j
@Component
public class SeatAvailabilityCache {

    private static final String PREFIX = "seatlock:cache:seats:";

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final Duration ttl;

    public SeatAvailabilityCache(StringRedisTemplate redis, JsonMapper json,
                                 @Value("${seatlock.cache.seat-map-ttl:5s}") Duration ttl) {
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
    }

    public Optional<List<SeatResponse>> get(Long eventId) {
        try {
            String cached = redis.opsForValue().get(PREFIX + eventId);
            return Optional.ofNullable(cached).map(s -> Arrays.asList(json.readValue(s, SeatResponse[].class)));
        } catch (RuntimeException e) {
            log.debug("Seat cache read failed, using database", e);
            return Optional.empty();
        }
    }

    public void put(Long eventId, List<SeatResponse> seats) {
        try {
            redis.opsForValue().set(PREFIX + eventId, json.writeValueAsString(seats), ttl);
        } catch (RuntimeException e) {
            log.debug("Seat cache write failed", e);
        }
    }

    public void evict(Long eventId) {
        try {
            redis.delete(PREFIX + eventId);
        } catch (RuntimeException e) {
            log.debug("Seat cache eviction failed (TTL will expire it)", e);
        }
    }

    /** Evict only AFTER commit, so a reader can't re-cache the old state between write and commit. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatsChanged(SeatsChangedEvent event) {
        evict(event.eventId());
    }
}
