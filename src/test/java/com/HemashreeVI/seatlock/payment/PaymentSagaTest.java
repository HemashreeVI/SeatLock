package com.HemashreeVI.seatlock.payment;

import com.HemashreeVI.seatlock.notification.NotificationRepository;
import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** End-to-end saga: HTTP -> outbox -> Kafka -> payment -> Kafka -> booking -> Kafka -> notification. */
class PaymentSagaTest extends IntegrationTest {

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    NotificationRepository notificationRepository;

    @Test
    void successfulPaymentConfirmsBookingAndNotifiesOnce() {
        long eventId = createEvent("VIP", 1, 2, 4000);
        List<Long> seats = seatIds(eventId);
        long bookingId = holdSeats("alice", eventId, seats);

        ResponseEntity<JsonNode> accepted = pay("alice", bookingId, "pay-ok-1", "tok_visa");
        assertThat(accepted.getStatusCode().value()).isEqualTo(202);
        assertThat(accepted.getBody().get("status").asString()).isEqualTo("PENDING");
        assertThat(accepted.getBody().get("amountCents").asLong()).isEqualTo(8000);
        long paymentId = accepted.getBody().get("id").asLong();

        awaitBookingStatus("alice", bookingId, "CONFIRMED");
        assertThat(get("/api/payments/" + paymentId, "alice").getBody().get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(get("/api/events/" + eventId + "/seats?status=BOOKED", null).getBody().size()).isEqualTo(2);

        // network retry with the same key: same payment, no second charge
        ResponseEntity<JsonNode> retry = pay("alice", bookingId, "pay-ok-1", "tok_visa");
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.getBody().get("id").asLong()).isEqualTo(paymentId);
        assertThat(paymentRepository.countByBookingId(bookingId)).isEqualTo(1);

        // a new key cannot pay the booking again; the same key with a different body is rejected
        assertThat(pay("alice", bookingId, "pay-ok-2", "tok_visa").getStatusCode().value()).isEqualTo(409);
        assertThat(pay("alice", bookingId, "pay-ok-1", "tok_other").getStatusCode().value()).isEqualTo(422);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationRepository.countByBookingIdAndType(bookingId, "BookingConfirmed")).isEqualTo(1));
        JsonNode inbox = get("/api/notifications", "alice").getBody();
        assertThat(inbox.valueStream().anyMatch(n -> n.get("bookingId").asLong() == bookingId)).isTrue();
    }

    @Test
    void failedPaymentCompensatesByReleasingSeats() {
        long eventId = createEvent("VIP", 1, 2, 4000);
        List<Long> seats = seatIds(eventId);
        long bookingId = holdSeats("alice", eventId, seats);

        long paymentId = pay("alice", bookingId, "pay-fail-1", "tok_fail_card").getBody().get("id").asLong();

        awaitBookingStatus("alice", bookingId, "CANCELLED");
        JsonNode payment = get("/api/payments/" + paymentId, "alice").getBody();
        assertThat(payment.get("status").asString()).isEqualTo("FAILED");
        assertThat(payment.get("failureReason").asString()).isEqualTo("Card declined");
        assertThat(get("/api/events/" + eventId + "/seats?status=AVAILABLE", null).getBody().size()).isEqualTo(2);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationRepository.countByBookingIdAndType(bookingId, "BookingCancelled")).isEqualTo(1));
    }

    @Test
    void paymentInProgressBlocksSecondPaymentAndCancellation() {
        long eventId = createEvent("VIP", 1, 1, 4000);
        long bookingId = holdSeats("alice", eventId, seatIds(eventId));
        assertThat(post("/api/bookings/" + bookingId + "/payments", Map.of("paymentToken", "tok_visa"), "alice", Map.of())
                .getStatusCode().value()).as("Idempotency-Key is required").isEqualTo(400);

        pay("alice", bookingId, "pay-a", "tok_visa");
        ResponseEntity<JsonNode> second = pay("alice", bookingId, "pay-b", "tok_visa");
        assertThat(second.getStatusCode().value()).isEqualTo(409);
        awaitBookingStatus("alice", bookingId, "CONFIRMED");
        assertThat(paymentRepository.countByBookingId(bookingId)).isEqualTo(1);
    }

    private void awaitBookingStatus(String user, long bookingId, String status) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).untilAsserted(() ->
                assertThat(get("/api/bookings/" + bookingId, user).getBody().get("status").asString()).isEqualTo(status));
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
