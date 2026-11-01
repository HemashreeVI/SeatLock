package com.HemashreeVI.seatlock.messaging;

import com.HemashreeVI.seatlock.booking.BookingExpiryService;
import com.HemashreeVI.seatlock.notification.NotificationRepository;
import com.HemashreeVI.seatlock.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OutboxRelayTest extends IntegrationTest {

    @Autowired
    BookingExpiryService expiryService;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void expiryIsPublishedThroughOutboxAndNotified() {
        long eventId = createEvent("VIP", 1, 1, 100);
        long bookingId = post("/api/bookings", Map.of("eventId", eventId, "seatIds", seatIds(eventId)), "dave", Map.of())
                .getBody().get("id").asLong();

        jdbc.update("update bookings set expires_at = now() - interval '1 second' where id = ?", bookingId);
        expiryService.expireBatch();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationRepository.countByBookingIdAndType(bookingId, "BookingExpired")).isEqualTo(1));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(outboxRepository.countByPublishedAtIsNull()).isZero());
    }

    @Test
    void redeliveredBookingEventIsNotNotifiedTwice() {
        long eventId = createEvent("VIP", 1, 1, 100);
        long bookingId = post("/api/bookings", Map.of("eventId", eventId, "seatIds", seatIds(eventId)), "erin", Map.of())
                .getBody().get("id").asLong();
        delete("/api/bookings/" + bookingId, "erin");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationRepository.countByBookingIdAndType(bookingId, "BookingCancelled")).isEqualTo(1));

        // simulate an at-least-once redelivery: mark the outbox row unpublished so it is sent again
        List<Long> ids = jdbc.queryForList("select id from outbox_events where aggregate_id = ? and event_type = 'BookingCancelled'",
                Long.class, String.valueOf(bookingId));
        jdbc.update("update outbox_events set published_at = null where id = ?", ids.getFirst());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outboxRepository.countByPublishedAtIsNull()).isZero());
        // give the consumer time to (not) act on the duplicate
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(notificationRepository.countByBookingIdAndType(bookingId, "BookingCancelled")).isEqualTo(1));
    }
}
