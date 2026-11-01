package com.HemashreeVI.seatlock.booking.locking;

import com.HemashreeVI.seatlock.booking.SeatUnavailableException;
import com.HemashreeVI.seatlock.config.LockingProperties;
import com.HemashreeVI.seatlock.seat.Seat;
import com.HemashreeVI.seatlock.seat.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Distributed lock in Redis: SET seatlock:seat-lock:{id} {token} NX PX {ttl} for every seat.
 * Losers fail FAST (no waiting, no DB connection used) -> HTTP 409.
 * Works across many app instances and keeps contention away from Postgres.
 * <p>
 * Caveat: a Redis lock alone is not a correctness guarantee (e.g. a GC pause longer than the
 * TTL). That's why the DB @Version check and unique index still back it up.
 */
@Component
@RequiredArgsConstructor
public class RedisSeatLockStrategy implements SeatLockStrategy {

    /** Delete the key only if WE still own it (compare-and-delete must be atomic -> Lua). */
    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """, Long.class);

    private final StringRedisTemplate redis;
    private final SeatRepository seatRepository;
    private final LockingProperties properties;

    @Override
    public LockingStrategyType type() {
        return LockingStrategyType.REDIS;
    }

    @Override
    public <T> T withSeatsLocked(List<Long> sortedSeatIds, Supplier<T> action) {
        String token = UUID.randomUUID().toString();
        List<String> acquired = new ArrayList<>();
        try {
            for (Long seatId : sortedSeatIds) {
                String key = "seatlock:seat-lock:" + seatId;
                Boolean ok = redis.opsForValue().setIfAbsent(key, token, properties.redisLockTtl());
                if (!Boolean.TRUE.equals(ok)) {
                    throw new SeatUnavailableException(List.of(seatId), "Seat is being booked by someone else");
                }
                acquired.add(key);
            }
            return action.get();
        } finally {
            for (String key : acquired) {
                redis.execute(RELEASE, List.of(key), token);
            }
        }
    }

    @Override
    public List<Seat> loadSeats(List<Long> sortedSeatIds) {
        return seatRepository.findAllByIdOrdered(sortedSeatIds);
    }
}
