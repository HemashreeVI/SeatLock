package com.HemashreeVI.seatlock.demo;

import com.HemashreeVI.seatlock.messaging.LocalOutboxDispatcher;
import com.HemashreeVI.seatlock.messaging.OutboxRepository;
import com.HemashreeVI.seatlock.notification.NotificationRepository;
import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** The free-tier "demo" profile: same behaviour as the Kafka setup, without a broker. */
@ActiveProfiles("demo")
class DemoModeTest extends IntegrationTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    OutboxRepository outboxRepository;

    @Test
    void runsWithoutKafka() {
        assertThat(context.getBeansOfType(KafkaTemplate.class)).isEmpty();
        assertThat(context.getBeansOfType(LocalOutboxDispatcher.class)).hasSize(1);
        assertThat(get("/actuator/health", null).getBody().get("status").asString()).isEqualTo("UP");
    }

    @Test
    void seedsAnUpcomingLineUpWithPricesAndSeatsLeft() {
        List<JsonNode> events = get("/api/events", null).getBody().valueStream().toList();
        for (String name : List.of("Neon Nights: Indie Music Festival", "The Laugh Riot: Stand-up Special",
                "City Derby: Football Finals", "Romeo & Juliet: A Modern Retelling", "Symphony Under the Stars")) {
            JsonNode event = events.stream().filter(e -> e.get("name").asString().equals(name)).findFirst().orElseThrow();
            assertThat(event.get("category").asString()).isNotEqualTo("OTHER");
            assertThat(event.get("availableSeats").asLong()).isPositive();
            assertThat(event.get("minPriceCents").asLong()).isPositive();
            assertThat(event.get("totalSeats").asLong()).isGreaterThanOrEqualTo(event.get("availableSeats").asLong());
        }
    }

    @Test
    void paymentSagaWorksInProcess() {
        long eventId = createEvent("VIP", 1, 2, 4000);
        List<Long> seats = seatIds(eventId);

        long paid = hold("demo-alice", eventId, seats.subList(0, 1));
        ResponseEntity<JsonNode> accepted = pay("demo-alice", paid, "demo-pay-1", "tok_visa");
        assertThat(accepted.getStatusCode().value()).isEqualTo(202);
        awaitStatus("demo-alice", paid, "CONFIRMED");

        long declined = hold("demo-bob", eventId, seats.subList(1, 2));
        pay("demo-bob", declined, "demo-pay-2", "tok_fail_card");
        awaitStatus("demo-bob", declined, "CANCELLED");
        assertThat(get("/api/events/" + eventId + "/seats?status=AVAILABLE", null).getBody().size()).isEqualTo(1);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(notificationRepository.countByBookingIdAndType(paid, "BookingConfirmed")).isEqualTo(1);
            assertThat(notificationRepository.countByBookingIdAndType(declined, "BookingCancelled")).isEqualTo(1);
            assertThat(outboxRepository.countByPublishedAtIsNull()).isZero();
        });
    }

    @Test
    void publicDeploymentDoesNotExposeMetrics() {
        assertThat(get("/actuator/prometheus", null).getStatusCode().value()).isEqualTo(404);
        assertThat(get("/actuator/metrics", null).getStatusCode().value()).isEqualTo(404);
    }

    private long hold(String user, long eventId, List<Long> seats) {
        return post("/api/bookings", Map.of("eventId", eventId, "seatIds", seats), user, Map.of())
                .getBody().get("id").asLong();
    }

    private ResponseEntity<JsonNode> pay(String user, long bookingId, String key, String token) {
        return post("/api/bookings/" + bookingId + "/payments", Map.of("paymentToken", token), user,
                Map.of("Idempotency-Key", key));
    }

    private void awaitStatus(String user, long bookingId, String expected) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(get("/api/bookings/" + bookingId, user).getBody().get("status").asString()).isEqualTo(expected));
    }
}
