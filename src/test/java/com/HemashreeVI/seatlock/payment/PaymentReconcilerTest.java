package com.HemashreeVI.seatlock.payment;

import com.HemashreeVI.seatlock.messaging.Topics;
import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** A provider outage longer than the Kafka retries must not leave seats stuck forever. */
class PaymentReconcilerTest extends IntegrationTest {

    @Autowired
    FakePaymentGateway gateway;

    @Autowired
    PaymentReconciler reconciler;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void providerBackUp() {
        gateway.setOutage(false);
    }

    @Test
    void stuckPaymentIsParkedOnDeadLetterTopicThenReDriven() throws Exception {
        long dltBefore = deadLetterCount();
        gateway.setOutage(true);
        long bookingId = holdAndPay("ruth", "pay-outage-1");
        long paymentId = paymentRepository.findByIdempotencyKey("pay-outage-1").orElseThrow().getId();

        // Kafka retries a few times, then parks the message on the 3-partition dead-letter topic
        await().atMost(Duration.ofSeconds(30)).until(() -> deadLetterCount() > dltBefore);
        assertThat(status("ruth", bookingId)).isEqualTo("PAYMENT_PENDING");
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);

        // the provider recovers; once the payment is older than pending-timeout, the reconciler finishes it
        gateway.setOutage(false);
        backdate(paymentId, "2 minutes");
        reconciler.reconcile();

        awaitStatus("ruth", bookingId, "CONFIRMED");
        assertThat(paymentRepository.countByBookingId(bookingId)).isEqualTo(1);
    }

    @Test
    void providerDownTooLongFailsPaymentAndReleasesSeats() {
        gateway.setOutage(true);
        long bookingId = holdAndPay("sam", "pay-outage-2");
        long paymentId = paymentRepository.findByIdempotencyKey("pay-outage-2").orElseThrow().getId();

        backdate(paymentId, "20 minutes");   // older than give-up-after (15m)
        reconciler.reconcile();

        awaitStatus("sam", bookingId, "CANCELLED");
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Payment timed out");
        JsonNode booking = get("/api/bookings/" + bookingId, "sam").getBody();
        assertThat(booking.get("seats").get(0).get("status").asString()).isEqualTo("AVAILABLE");
    }

    @Test
    void deadLetterTopicsHaveAsManyPartitionsAsTheirSourceTopics() throws Exception {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            Map<String, TopicDescription> topics = admin.describeTopics(List.of(
                    Topics.PAYMENT_REQUESTED, Topics.PAYMENT_REQUESTED + Topics.DLT_SUFFIX,
                    Topics.PAYMENT_COMPLETED + Topics.DLT_SUFFIX, Topics.BOOKING_EVENTS + Topics.DLT_SUFFIX))
                    .allTopicNames().get();
            int source = topics.get(Topics.PAYMENT_REQUESTED).partitions().size();
            topics.values().forEach(t -> assertThat(t.partitions()).as(t.name()).hasSize(source));
        }
    }

    private long holdAndPay(String user, String key) {
        long eventId = createEvent("VIP", 1, 1, 1000);
        long bookingId = post("/api/bookings", Map.of("eventId", eventId, "seatIds", seatIds(eventId)), user, Map.of())
                .getBody().get("id").asLong();
        assertThat(post("/api/bookings/" + bookingId + "/payments", Map.of("paymentToken", "tok_visa"), user,
                Map.of("Idempotency-Key", key)).getStatusCode().value()).isEqualTo(202);
        return bookingId;
    }

    private void backdate(long paymentId, String interval) {
        jdbc.update("update payments set created_at = now() - interval '" + interval + "' where id = ?", paymentId);
    }

    private String status(String user, long bookingId) {
        return get("/api/bookings/" + bookingId, user).getBody().get("status").asString();
    }

    private void awaitStatus(String user, long bookingId, String expected) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(status(user, bookingId)).isEqualTo(expected));
    }

    private long deadLetterCount() throws Exception {
        String dlt = Topics.PAYMENT_REQUESTED + Topics.DLT_SUFFIX;
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            int partitions = admin.describeTopics(List.of(dlt)).allTopicNames().get().get(dlt).partitions().size();
            Map<TopicPartition, OffsetSpec> request = new HashMap<>();
            for (int p = 0; p < partitions; p++) {
                request.put(new TopicPartition(dlt, p), OffsetSpec.latest());
            }
            return admin.listOffsets(request).all().get().values().stream().mapToLong(o -> o.offset()).sum();
        }
    }
}
