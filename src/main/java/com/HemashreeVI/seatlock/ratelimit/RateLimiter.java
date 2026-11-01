package com.HemashreeVI.seatlock.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token bucket in Redis, shared by every app instance.
 * <p>
 * Each client has a bucket of {@code capacity} tokens that refills at {@code refillPerSecond}.
 * A request costs one token; an empty bucket means HTTP 429. The whole read-refill-take step
 * runs as one Lua script, so concurrent requests can't both take the last token. Redis's own
 * clock (TIME) is used so app servers with skewed clocks agree.
 */
@Component
public class RateLimiter {

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> TOKEN_BUCKET = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local t = redis.call('TIME')
            local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

            local bucket = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(bucket[1])
            local ts = tonumber(bucket[2])
            if tokens == nil then
                tokens = capacity
                ts = now
            end

            tokens = math.min(capacity, tokens + (math.max(0, now - ts) / 1000.0) * refill)
            local allowed = 0
            local retryAfterMs = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            else
                retryAfterMs = math.ceil((1 - tokens) / refill * 1000)
            end

            redis.call('HSET', key, 'tokens', tostring(tokens), 'ts', tostring(now))
            redis.call('PEXPIRE', key, math.ceil(capacity / refill * 1000) + 1000)
            return {allowed, retryAfterMs, math.floor(tokens)}
            """, List.class);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public record Decision(boolean allowed, long retryAfterMs, long remaining) {
    }

    public Decision tryAcquire(String clientKey, int capacity, double refillPerSecond) {
        List<?> result = redis.execute(TOKEN_BUCKET, List.of("seatlock:ratelimit:" + clientKey),
                String.valueOf(capacity), String.valueOf(refillPerSecond));
        return new Decision(((Number) result.get(0)).longValue() == 1,
                ((Number) result.get(1)).longValue(),
                ((Number) result.get(2)).longValue());
    }
}
