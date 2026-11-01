package com.HemashreeVI.seatlock.payment;

import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentApiTest extends IntegrationTest {

    @Autowired
    PaymentRepository paymentRepository;

    @Test
    void successfulPaymentConfirmsBookingAndIsIdempotent() {
        long eventId = createEvent("VIP", 1, 2, 4000);
        List<Long> seats = seatIds(eventId);
        long bookingId = holdSeats("alice", eventId, seats);

        ResponseEntity<JsonNode> paid = pay("alice", bookingId, "pay-1", "tok_visa");
        assertThat(paid.getStatusCode().value()).isEqualTo(200);
        assertThat(paid.getBody().get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(paid.getBody().get("amountCents").asLong()).isEqualTo(8000);

        // network retry with the same key: same payment, no second charge
        ResponseEntity<JsonNode> retry = pay("alice", bookingId, "pay-1", "tok_visa");
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.getBody().get("id").asLong()).isEqualTo(paid.getBody().get("id").asLong());
        assertThat(paymentRepository.countByBookingId(bookingId)).isEqualTo(1);

        // a *new* key cannot pay the booking again
        assertThat(pay("alice", bookingId, "pay-2", "tok_visa").getStatusCode().value()).isEqualTo(409);
        // the same key with a different body is rejected
        assertThat(pay("alice", bookingId, "pay-1", "tok_other").getStatusCode().value()).isEqualTo(422);

        assertThat(get("/api/bookings/" + bookingId, "alice").getBody().get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(get("/api/events/" + eventId + "/seats?status=BOOKED", null).getBody().size()).isEqualTo(2);
    }

    @Test
    void failedPaymentReleasesSeats() {
        long eventId = createEvent("VIP", 1, 2, 4000);
        List<Long> seats = seatIds(eventId);
        long bookingId = holdSeats("alice", eventId, seats);

        ResponseEntity<JsonNode> paid = pay("alice", bookingId, "pay-fail", "tok_fail_card");
        assertThat(paid.getBody().get("status").asString()).isEqualTo("FAILED");
        assertThat(get("/api/bookings/" + bookingId, "alice").getBody().get("status").asString()).isEqualTo("CANCELLED");
        assertThat(get("/api/events/" + eventId + "/seats?status=AVAILABLE", null).getBody().size()).isEqualTo(2);
    }

    @Test
    void paymentRequiresIdempotencyKey() {
        long eventId = createEvent("VIP", 1, 1, 4000);
        long bookingId = holdSeats("alice", eventId, seatIds(eventId));
        assertThat(post("/api/bookings/" + bookingId + "/payments", Map.of("paymentToken", "tok_visa"), "alice", Map.of())
                .getStatusCode().value()).isEqualTo(400);
    }

    private long holdSeats(String user, long eventId, List<Long> seats) {
        return post("/api/bookings", Map.of("eventId", eventId, "seatIds", seats), user, Map.of())
                .getBody().get("id").asLong();
    }

    private ResponseEntity<JsonNode> pay(String user, long bookingId, String key, String token) {
        return post("/api/bookings/" + bookingId + "/payments", Map.of("paymentToken", token), user,
                Map.of("Idempotency-Key", key));
    }
}
