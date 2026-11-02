package com.HemashreeVI.seatlock.event;

import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventApiTest extends IntegrationTest {

    @Test
    void createsEventAndGeneratesSeats() {
        Map<String, Object> body = Map.of(
                "name", "Coldplay Live",
                "venue", "DY Patil Stadium",
                "startsAt", Instant.now().plus(60, ChronoUnit.DAYS).toString(),
                "sections", List.of(
                        Map.of("name", "VIP", "rows", 2, "seatsPerRow", 5, "priceCents", 500000),
                        Map.of("name", "GENERAL", "rows", 3, "seatsPerRow", 10, "priceCents", 150000)));

        ResponseEntity<JsonNode> created = post("/api/events", body, null, admin());

        assertThat(created.getStatusCode().value()).isEqualTo(201);
        long id = created.getBody().get("id").asLong();
        assertThat(created.getHeaders().getLocation()).hasToString("/api/events/" + id);

        JsonNode seats = get("/api/events/" + id + "/seats", null).getBody();
        assertThat(seats.size()).isEqualTo(2 * 5 + 3 * 10);
        assertThat(seats.get(0).get("status").asString()).isEqualTo("AVAILABLE");

        JsonNode available = get("/api/events/" + id + "/seats?status=AVAILABLE", null).getBody();
        assertThat(available.size()).isEqualTo(40);
    }

    @Test
    void unknownEventIs404() {
        assertThat(get("/api/events/999999", null).getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/events/999999/seats", null).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void rejectsInvalidInput() {
        Map<String, Object> pastEvent = Map.of(
                "name", "Old", "venue", "X", "startsAt", "2020-01-01T00:00:00Z",
                "sections", List.of(Map.of("name", "A", "rows", 1, "seatsPerRow", 1, "priceCents", 100)));
        assertThat(post("/api/events", pastEvent, null, admin()).getStatusCode().value()).isEqualTo(400);

        Map<String, Object> duplicateSections = Map.of(
                "name", "Dup", "venue", "X", "startsAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                "sections", List.of(
                        Map.of("name", "A", "rows", 1, "seatsPerRow", 1, "priceCents", 100),
                        Map.of("name", "A", "rows", 1, "seatsPerRow", 1, "priceCents", 100)));
        assertThat(post("/api/events", duplicateSections, null, admin()).getStatusCode().value()).isEqualTo(400);
    }
}
