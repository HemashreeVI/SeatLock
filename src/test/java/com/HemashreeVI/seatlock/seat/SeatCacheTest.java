package com.HemashreeVI.seatlock.seat;

import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeatCacheTest extends IntegrationTest {

    @Test
    void seatMapIsCachedAndEvictedWhenSeatsChange() {
        long eventId = createEvent("PIT", 1, 4, 100);

        ResponseEntity<JsonNode> first = get("/api/events/" + eventId + "/seats", null);
        ResponseEntity<JsonNode> second = get("/api/events/" + eventId + "/seats", null);
        assertThat(first.getHeaders().getFirst("X-Cache")).isEqualTo("MISS");
        assertThat(second.getHeaders().getFirst("X-Cache")).isEqualTo("HIT");
        assertThat(second.getBody().size()).isEqualTo(4);

        List<Long> seats = seatIds(eventId);
        post("/api/bookings", Map.of("eventId", eventId, "seatIds", seats.subList(0, 1)), "frank", Map.of());

        ResponseEntity<JsonNode> afterHold = get("/api/events/" + eventId + "/seats?status=AVAILABLE", null);
        assertThat(afterHold.getHeaders().getFirst("X-Cache")).isEqualTo("MISS");
        assertThat(afterHold.getBody().size()).isEqualTo(3);
    }
}
