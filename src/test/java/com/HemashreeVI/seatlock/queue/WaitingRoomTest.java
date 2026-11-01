package com.HemashreeVI.seatlock.queue;

import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WaitingRoomTest extends IntegrationTest {

    @Autowired
    WaitingRoomService waitingRoom;

    @Test
    void hotEventRequiresAdmissionAndAdmitsInFifoOrder() {
        long eventId = createHotEvent();
        List<Long> seats = seatIds(eventId);

        // booking without going through the queue is refused
        assertThat(hold("u1", eventId, seats.subList(0, 1), null).getStatusCode().value()).isEqualTo(403);

        for (String user : List.of("u1", "u2", "u3", "u4")) {
            assertThat(post("/api/events/" + eventId + "/queue", null, user, Map.of())
                    .getBody().get("state").asString()).isEqualTo("WAITING");
        }
        assertThat(queue("u3").get("position").asLong()).isEqualTo(3);
        // re-joining keeps your place
        assertThat(post("/api/events/" + eventId + "/queue", null, "u1", Map.of()).getBody().get("position").asLong())
                .isEqualTo(1);

        // let 2 people in (room for 2)
        assertThat(waitingRoom.admit(eventId, 2, 10)).containsExactly("u1", "u2");
        assertThat(queue("u3").get("position").asLong()).isEqualTo(1);
        // the room is full: nobody else gets in until admissions expire
        assertThat(waitingRoom.admit(eventId, 2, 10)).isEmpty();

        JsonNode admitted = queue("u1");
        assertThat(admitted.get("state").asString()).isEqualTo("ADMITTED");
        String token = admitted.get("admissionToken").asString();

        assertThat(hold("u1", eventId, seats.subList(0, 1), "wrong-token").getStatusCode().value()).isEqualTo(403);
        assertThat(hold("u1", eventId, seats.subList(0, 1), token).getStatusCode().value()).isEqualTo(201);
        // someone else's token doesn't work
        assertThat(hold("u3", eventId, seats.subList(1, 2), token).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void normalEventsHaveNoWaitingRoom() {
        long eventId = createEvent("GA", 1, 1, 100);
        assertThat(post("/api/events/" + eventId + "/queue", null, "u1", Map.of()).getStatusCode().value()).isEqualTo(400);
    }

    private JsonNode queue(String user) {
        long eventId = lastHotEvent;
        return get("/api/events/" + eventId + "/queue", user).getBody();
    }

    private long lastHotEvent;

    private long createHotEvent() {
        Map<String, Object> body = Map.of(
                "name", "Hot Event", "venue", "Arena",
                "startsAt", Instant.now().plus(10, ChronoUnit.DAYS).toString(),
                "queueEnabled", true,
                "sections", List.of(Map.of("name", "A", "rows", 1, "seatsPerRow", 5, "priceCents", 100)));
        lastHotEvent = post("/api/events", body, null, Map.of()).getBody().get("id").asLong();
        return lastHotEvent;
    }

    private ResponseEntity<JsonNode> hold(String user, long eventId, List<Long> seats, String token) {
        Map<String, String> headers = token == null ? Map.of() : Map.of("X-Queue-Token", token);
        return post("/api/bookings", Map.of("eventId", eventId, "seatIds", seats), user, headers);
    }
}
