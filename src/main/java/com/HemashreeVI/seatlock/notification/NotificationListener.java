package com.HemashreeVI.seatlock.notification;

import com.HemashreeVI.seatlock.messaging.Messages;
import com.HemashreeVI.seatlock.messaging.OutboxRelay;
import com.HemashreeVI.seatlock.messaging.ProcessedMessages;
import com.HemashreeVI.seatlock.messaging.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends the user a message whenever their booking is confirmed, cancelled or expires.
 * Uses the inbox table so a redelivered Kafka message never sends a second email.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private static final String CONSUMER = "notifications";

    private final NotificationRepository repository;
    private final ProcessedMessages processedMessages;
    private final JsonMapper json;

    @KafkaListener(topics = Topics.BOOKING_EVENTS, groupId = "seatlock-notifications")
    @Transactional
    public void onBookingEvent(@Payload String payload, @Header(OutboxRelay.HEADER_EVENT_ID) String eventId) {
        if (!processedMessages.markProcessed(CONSUMER, eventId)) {
            log.info("Booking event {} already notified, skipping", eventId);
            return;
        }
        Messages.BookingEvent event = json.readValue(payload, Messages.BookingEvent.class);
        String message = switch (event.type()) {
            case "BookingConfirmed" -> "Your booking #%d is confirmed (%d seat(s)). Enjoy the show!"
                    .formatted(event.bookingId(), event.seatIds().size());
            case "BookingExpired" -> "Your hold on booking #%d expired and the seats were released."
                    .formatted(event.bookingId());
            default -> "Your booking #%d was cancelled and the seats were released.".formatted(event.bookingId());
        };
        Notification notification = new Notification();
        notification.setBookingId(event.bookingId());
        notification.setUserId(event.userId());
        notification.setType(event.type());
        notification.setMessage(message);
        repository.save(notification);
        log.info("Notification to {}: {}", event.userId(), message);
    }
}
