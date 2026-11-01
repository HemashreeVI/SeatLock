package com.HemashreeVI.seatlock.ratelimit;

import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitTest extends IntegrationTest {

    @Autowired
    RateLimiter rateLimiter;

    @Test
    void tokenBucketAllowsBurstThenBlocks() {
        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            if (rateLimiter.tryAcquire("unit-test-bucket", 5, 0.001).allowed()) {
                allowed++;
            }
        }
        assertThat(allowed).isEqualTo(5);
        RateLimiter.Decision blocked = rateLimiter.tryAcquire("unit-test-bucket", 5, 0.001);
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterMs()).isPositive();
    }

    @Test
    void floodingTheBookingEndpointGets429() {
        long eventId = createEvent("PIT", 1, 1, 100);
        int tooMany = 0;
        ResponseEntity<JsonNode> last = null;
        for (int i = 0; i < 80; i++) {
            // invalid body on purpose: the limiter runs before the controller, so it still counts
            ResponseEntity<JsonNode> res = post("/api/bookings", Map.of("eventId", eventId), "spammer", Map.of());
            if (res.getStatusCode().value() == 429) {
                tooMany++;
                last = res;
            }
        }
        assertThat(tooMany).isGreaterThan(10);
        assertThat(last.getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(last.getBody().get("title").asString()).isEqualTo("Too many requests");

        // other users are unaffected
        assertThat(post("/api/bookings", Map.of("eventId", eventId), "innocent", Map.of()).getStatusCode().value())
                .isEqualTo(400);
    }
}
