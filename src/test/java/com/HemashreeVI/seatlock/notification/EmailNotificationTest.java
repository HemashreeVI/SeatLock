package com.HemashreeVI.seatlock.notification;

import com.HemashreeVI.seatlock.notification.email.Email;
import com.HemashreeVI.seatlock.notification.email.EmailSender;
import com.HemashreeVI.seatlock.notification.email.LoggingEmailSender;
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

class EmailNotificationTest extends IntegrationTest {

    @Autowired
    EmailSender emailSender;   // LoggingEmailSender: no Brevo key in tests

    @Autowired
    NotificationRepository notificationRepository;

    @Test
    void confirmationAndCancellationAreEmailedToTheCustomer() {
        long eventId = createEvent("GOLD", 1, 3, 250000);
        List<Long> seats = seatIds(eventId);

        ResponseEntity<JsonNode> held = post("/api/bookings", Map.of("eventId", eventId, "seatIds", seats.subList(0, 2),
                "name", "Hema <b>", "email", "Hema@Example.com"), "mail-user", Map.of());
        assertThat(held.getStatusCode().value()).isEqualTo(201);
        assertThat(held.getBody().get("customerEmail").asString()).isEqualTo("hema@example.com");
        long paid = held.getBody().get("id").asLong();
        post("/api/bookings/" + paid + "/payments", Map.of("paymentToken", "tok_visa"), "mail-user",
                Map.of("Idempotency-Key", "mail-pay-1"));

        Email confirmation = awaitEmail("hema@example.com", "Booking #" + paid + " confirmed");
        assertThat(confirmation.html()).contains("GOLD A1, GOLD A2").contains("₹5,000.00").contains("Hi Hema &lt;b&gt;,");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
                notificationRepository.findFirstByBookingIdAndType(paid, "BookingConfirmed").orElseThrow().getEmailStatus())
                .isEqualTo(EmailStatus.LOGGED));   // SENT when a real provider (Brevo) is configured

        long cancelled = post("/api/bookings", Map.of("eventId", eventId, "seatIds", seats.subList(2, 3),
                "email", "hema@example.com"), "mail-user", Map.of()).getBody().get("id").asLong();
        delete("/api/bookings/" + cancelled, "mail-user");
        awaitEmail("hema@example.com", "Booking #" + cancelled + " cancelled");

        JsonNode inbox = get("/api/notifications", "mail-user").getBody();
        assertThat(inbox.valueStream().anyMatch(n -> "LOGGED".equals(n.get("emailStatus").asString()))).isTrue();
    }

    @Test
    void noAddressMeansNoEmail() {
        long eventId = createEvent("GOLD", 1, 1, 100);
        long bookingId = post("/api/bookings", Map.of("eventId", eventId, "seatIds", seatIds(eventId)), "quiet-user", Map.of())
                .getBody().get("id").asLong();
        delete("/api/bookings/" + bookingId, "quiet-user");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                notificationRepository.findFirstByBookingIdAndType(bookingId, "BookingCancelled")).isPresent());
        assertThat(notificationRepository.findFirstByBookingIdAndType(bookingId, "BookingCancelled").orElseThrow()
                .getEmailStatus()).isEqualTo(EmailStatus.NONE);
    }

    @Test
    void invalidEmailIsRejected() {
        long eventId = createEvent("GOLD", 1, 1, 100);
        assertThat(post("/api/bookings", Map.of("eventId", eventId, "seatIds", seatIds(eventId), "email", "not-an-email"),
                "typo-user", Map.of()).getStatusCode().value()).isEqualTo(400);
    }

    private Email awaitEmail(String to, String subjectStart) {
        LoggingEmailSender log = (LoggingEmailSender) emailSender;
        await().atMost(Duration.ofSeconds(20)).until(() -> log.sent().stream()
                .anyMatch(e -> e.to().equals(to) && e.subject().startsWith(subjectStart)));
        return log.sent().stream().filter(e -> e.to().equals(to) && e.subject().startsWith(subjectStart)).findFirst().orElseThrow();
    }
}
