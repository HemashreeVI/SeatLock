package com.HemashreeVI.seatlock.booking;

import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BookingApiTest extends IntegrationTest {

    @Autowired
    BookingExpiryService expiryService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void holdThenSeatIsUnavailableToOthers() {
        long eventId = createEvent("VIP", 1, 5, 2500);
        List<Long> seats = seatIds(eventId);

        ResponseEntity<JsonNode> held = hold("alice", eventId, seats.subList(0, 2), null);
        assertThat(held.getStatusCode().value()).isEqualTo(201);
        assertThat(held.getBody().get("status").asString()).isEqualTo("HELD");
        assertThat(held.getBody().get("totalCents").asLong()).isEqualTo(5000);
        assertThat(held.getBody().get("expiresAt").asString()).isNotBlank();

        ResponseEntity<JsonNode> conflict = hold("bob", eventId, seats.subList(1, 3), null);
        assertThat(conflict.getStatusCode().value()).isEqualTo(409);
        assertThat(conflict.getBody().get("seatIds").get(0).asLong()).isEqualTo(seats.get(1));

        JsonNode available = get("/api/events/" + eventId + "/seats?status=AVAILABLE", null).getBody();
        assertThat(available.size()).isEqualTo(3);
    }

    @Test
    void bookingsArePrivateAndCancellable() {
        long eventId = createEvent("VIP", 1, 3, 100);
        List<Long> seats = seatIds(eventId);
        long bookingId = hold("alice", eventId, seats.subList(0, 1), null).getBody().get("id").asLong();

        assertThat(get("/api/bookings/" + bookingId, "alice").getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/bookings/" + bookingId, "mallory").getStatusCode().value()).isEqualTo(404);
        assertThat(delete("/api/bookings/" + bookingId, "mallory").getStatusCode().value()).isEqualTo(404);

        ResponseEntity<JsonNode> cancelled = delete("/api/bookings/" + bookingId, "alice");
        assertThat(cancelled.getBody().get("status").asString()).isEqualTo("CANCELLED");
        assertThat(delete("/api/bookings/" + bookingId, "alice").getStatusCode().value()).isEqualTo(409);

        // the seat is free again
        assertThat(hold("bob", eventId, seats.subList(0, 1), null).getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void validatesRequests() {
        long eventId = createEvent("VIP", 1, 3, 100);
        List<Long> seats = seatIds(eventId);

        // not logged in
        assertThat(post("/api/bookings", Map.of("eventId", eventId, "seatIds", seats), null, Map.of())
                .getStatusCode().value()).isEqualTo(401);
        // duplicate seat ids
        assertThat(hold("alice", eventId, List.of(seats.get(0), seats.get(0)), null).getStatusCode().value())
                .isEqualTo(400);
        // seat from another event
        long otherEvent = createEvent("VIP", 1, 1, 100);
        assertThat(hold("alice", otherEvent, seats.subList(0, 1), null).getStatusCode().value()).isEqualTo(400);
        // unknown seat
        assertThat(hold("alice", eventId, List.of(999_999_999L), null).getStatusCode().value()).isEqualTo(404);
        // too many seats
        long bigEvent = createEvent("BIG", 1, 20, 100);
        assertThat(hold("alice", bigEvent, seatIds(bigEvent).subList(0, 11), null).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void idempotentHoldReturnsSameBooking() {
        long eventId = createEvent("VIP", 1, 5, 100);
        List<Long> seats = seatIds(eventId);

        ResponseEntity<JsonNode> first = hold("alice", eventId, seats.subList(0, 2), "key-123");
        ResponseEntity<JsonNode> retry = hold("alice", eventId, seats.subList(0, 2), "key-123");

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(retry.getStatusCode().value()).isEqualTo(200);
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.getBody().get("id").asLong()).isEqualTo(first.getBody().get("id").asLong());
        assertThat(jdbc.queryForObject("select count(*) from bookings where user_id = 'alice' and idempotency_key = 'key-123'",
                Integer.class)).isEqualTo(1);

        // same key, different request -> 422
        assertThat(hold("alice", eventId, seats.subList(2, 3), "key-123").getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void expiredHoldsAreReleased() {
        long eventId = createEvent("VIP", 1, 2, 100);
        List<Long> seats = seatIds(eventId);
        long bookingId = hold("alice", eventId, seats, null).getBody().get("id").asLong();

        // pretend 10 minutes have passed
        jdbc.update("update bookings set expires_at = now() - interval '1 second' where id = ?", bookingId);
        expiryService.expireBatch();

        assertThat(get("/api/bookings/" + bookingId, "alice").getBody().get("status").asString()).isEqualTo("EXPIRED");
        assertThat(get("/api/events/" + eventId + "/seats?status=AVAILABLE", null).getBody().size()).isEqualTo(2);
        assertThat(hold("bob", eventId, seats, null).getStatusCode().value()).isEqualTo(201);
    }

    protected ResponseEntity<JsonNode> hold(String user, long eventId, List<Long> seatIds, String key) {
        Map<String, String> headers = key == null ? Map.of() : Map.of("Idempotency-Key", key);
        return post("/api/bookings", Map.of("eventId", eventId, "seatIds", seatIds), user, headers);
    }
}
