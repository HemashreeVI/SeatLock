package com.HemashreeVI.seatlock.ratelimit;

import com.HemashreeVI.seatlock.config.RateLimitProperties;
import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitTest extends IntegrationTest {

    @Autowired
    RateLimiter rateLimiter;

    @Autowired
    JsonMapper json;

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
        int tooMany = flood("/api/bookings", eventId, "spammer");
        assertThat(tooMany).isGreaterThan(10);

        ResponseEntity<JsonNode> last = post("/api/bookings", Map.of("eventId", eventId), "spammer", Map.of());
        assertThat(last.getStatusCode().value()).isEqualTo(429);
        assertThat(last.getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(last.getBody().get("title").asString()).isEqualTo("Too many requests");

        // other users are unaffected
        assertThat(post("/api/bookings", Map.of("eventId", eventId), "innocent", Map.of()).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void matrixParametersInThePathDoNotBypassTheLimiter() {
        long eventId = createEvent("PIT", 1, 1, 100);
        // Spring MVC routes "/api/bookings;v=1" to the booking controller, so it must be limited too
        assertThat(flood("/api/bookings;v=1", eventId, "sneaky")).isGreaterThan(10);
    }

    @Test
    void rotatingUserIdsIsCappedByThePerIpBucket() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(rateLimiter,
                new RateLimitProperties(true, 1000, 1000, 10, 0.001), json);
        int tooMany = 0;
        for (int i = 0; i < 30; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/bookings");
            request.setRemoteAddr("203.0.113.7");
            request.addHeader("X-User-Id", "fresh-user-" + i);   // a new identity every time
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            if (response.getStatus() == 429) {
                tooMany++;
            }
        }
        assertThat(tooMany).isEqualTo(20);
    }

    private int flood(String path, long eventId, String user) {
        int tooMany = 0;
        for (int i = 0; i < 80; i++) {
            // invalid body on purpose: the limiter runs before the controller, so it still counts
            if (post(path, Map.of("eventId", eventId), user, Map.of()).getStatusCode().value() == 429) {
                tooMany++;
            }
        }
        return tooMany;
    }
}
