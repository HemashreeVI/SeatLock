package com.HemashreeVI.seatlock.queue;

import com.HemashreeVI.seatlock.common.BadRequestException;
import com.HemashreeVI.seatlock.config.QueueProperties;
import com.HemashreeVI.seatlock.event.Event;
import com.HemashreeVI.seatlock.event.EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Virtual waiting room for hot events.
 * <p>
 * Instead of letting 50,000 people hit "book" at the same second, everyone joins a FIFO line
 * (a Redis sorted set scored by join time). A background job lets people in at a controlled
 * rate, keeping at most {@code maxActiveUsers} shoppers active per event. Admitted users get a
 * short-lived token that the booking endpoint checks.
 * <pre>
 * seatlock:queue:events                 SET   events with someone waiting
 * seatlock:queue:{eventId}:waiting      ZSET  userId -> join time (FIFO)
 * seatlock:queue:{eventId}:admitted     ZSET  userId -> admission expiry
 * seatlock:queue:{eventId}:token:{user} STR   admission token (with TTL)
 * </pre>
 */
@Slf4j
@Service
public class WaitingRoomService {

    private static final String EVENTS_KEY = "seatlock:queue:events";

    /**
     * Atomically: drop expired admissions, compute free room, pop the next users in line,
     * mark them admitted and store their tokens. Atomic, so two app instances running the job
     * at the same time can never admit the same person twice or overfill the room.
     */
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> ADMIT = new DefaultRedisScript<>("""
            local waiting, admitted, tokenPrefix = KEYS[1], KEYS[2], KEYS[3]
            local now = tonumber(ARGV[1])
            local maxActive = tonumber(ARGV[2])
            local batch = tonumber(ARGV[3])
            local ttlMs = tonumber(ARGV[4])

            redis.call('ZREMRANGEBYSCORE', admitted, '-inf', now)
            local room = math.min(maxActive - redis.call('ZCARD', admitted), batch)
            if room <= 0 then return {} end

            local popped = redis.call('ZPOPMIN', waiting, room)
            local users = {}
            local tokenIndex = 5
            for i = 1, #popped, 2 do
                local user = popped[i]
                redis.call('ZADD', admitted, now + ttlMs, user)
                redis.call('SET', tokenPrefix .. user, ARGV[tokenIndex], 'PX', ttlMs)
                tokenIndex = tokenIndex + 1
                table.insert(users, user)
            end
            return users
            """, List.class);

    private final StringRedisTemplate redis;
    private final EventService eventService;
    private final QueueProperties properties;

    public WaitingRoomService(StringRedisTemplate redis, EventService eventService, QueueProperties properties) {
        this.redis = redis;
        this.eventService = eventService;
        this.properties = properties;
    }

    public QueueStatus join(Long eventId, String userId) {
        Event event = eventService.getEvent(eventId);
        if (!event.isQueueEnabled()) {
            throw new BadRequestException("Event " + eventId + " has no waiting room; book directly");
        }
        if (token(eventId, userId) == null) {
            // NX: re-joining never moves you to the back of the line
            redis.opsForZSet().addIfAbsent(waitingKey(eventId), userId, System.currentTimeMillis());
            redis.opsForSet().add(EVENTS_KEY, String.valueOf(eventId));
        }
        return status(eventId, userId);
    }

    public QueueStatus status(Long eventId, String userId) {
        String token = token(eventId, userId);
        if (token != null) {
            Long ttlMs = redis.getExpire(tokenKey(eventId, userId), java.util.concurrent.TimeUnit.MILLISECONDS);
            return QueueStatus.admitted(token, Instant.now().plusMillis(ttlMs == null ? 0 : Math.max(0, ttlMs)));
        }
        Long rank = redis.opsForZSet().rank(waitingKey(eventId), userId);
        if (rank == null) {
            return QueueStatus.notInQueue();
        }
        long position = rank + 1;
        // rough estimate: one admission batch per second
        long estimatedWait = (long) Math.ceil((double) position / Math.max(1, properties.admitBatchSize()));
        return QueueStatus.waiting(position, estimatedWait);
    }

    /** Throws unless the event has no waiting room or the user holds a valid admission token. */
    public void assertAdmitted(Long eventId, String userId, String providedToken) {
        if (!eventService.getEvent(eventId).isQueueEnabled()) {
            return;
        }
        String expected = token(eventId, userId);
        if (expected == null || !expected.equals(providedToken)) {
            throw new QueueAdmissionRequiredException(
                    "This event uses a waiting room: join via POST /api/events/" + eventId
                            + "/queue and send your admission token as X-Queue-Token");
        }
    }

    /** One admission tick for every event that has people waiting. */
    public void admitAll() {
        Set<String> events = redis.opsForSet().members(EVENTS_KEY);
        if (events == null) {
            return;
        }
        for (String eventId : events) {
            admit(Long.valueOf(eventId), properties.maxActiveUsers(), properties.admitBatchSize());
        }
    }

    /** Admits up to {@code batch} users from the head of the line, respecting {@code maxActive}. */
    public List<String> admit(Long eventId, int maxActive, int batch) {
        Duration ttl = properties.admissionTtl();
        List<String> args = new ArrayList<>(List.of(String.valueOf(System.currentTimeMillis()),
                String.valueOf(maxActive), String.valueOf(batch), String.valueOf(ttl.toMillis())));
        for (int i = 0; i < batch; i++) {
            args.add(UUID.randomUUID().toString());
        }
        @SuppressWarnings("unchecked")
        List<String> admitted = redis.execute(ADMIT,
                List.of(waitingKey(eventId), admittedKey(eventId), tokenKey(eventId, "")), args.toArray());
        Long stillWaiting = redis.opsForZSet().zCard(waitingKey(eventId));
        if (stillWaiting == null || stillWaiting == 0) {
            redis.opsForSet().remove(EVENTS_KEY, String.valueOf(eventId));
        }
        if (admitted != null && !admitted.isEmpty()) {
            log.info("Waiting room for event {}: admitted {} user(s), {} still waiting", eventId, admitted.size(), stillWaiting);
        }
        return admitted == null ? List.of() : admitted;
    }

    private String token(Long eventId, String userId) {
        return redis.opsForValue().get(tokenKey(eventId, userId));
    }

    private static String waitingKey(Long eventId) {
        return "seatlock:queue:" + eventId + ":waiting";
    }

    private static String admittedKey(Long eventId) {
        return "seatlock:queue:" + eventId + ":admitted";
    }

    private static String tokenKey(Long eventId, String userId) {
        return "seatlock:queue:" + eventId + ":token:" + userId;
    }
}
