package com.HemashreeVI.seatlock.booking;

import com.HemashreeVI.seatlock.messaging.MessageHandler;
import com.HemashreeVI.seatlock.messaging.Messages;
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
 * Saga step 3 (booking side): reacts to the payment result.
 * Success -> confirm the booking. Failure -> COMPENSATE: release the held seats.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultListener implements MessageHandler {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final JsonMapper json;

    @Override
    public String topic() {
        return Topics.PAYMENT_COMPLETED;
    }

    @Override
    @KafkaListener(topics = Topics.PAYMENT_COMPLETED, groupId = "seatlock-bookings")
    @Transactional
    public void handle(@Payload String payload, @Header(name = Topics.HEADER_EVENT_ID, required = false) String eventId) {
        Messages.PaymentCompleted result = json.readValue(payload, Messages.PaymentCompleted.class);
        Booking booking = bookingRepository.findByIdForUpdate(result.bookingId()).orElse(null);
        if (booking == null) {
            log.error("Payment {} completed for unknown booking {}", result.paymentId(), result.bookingId());
            return;
        }
        if (booking.getStatus() != BookingStatus.PAYMENT_PENDING) {
            // redelivered message (already handled) -> nothing to do. Idempotent by state.
            if (result.succeeded() && booking.getStatus() != BookingStatus.CONFIRMED) {
                log.error("Payment {} succeeded but booking {} is {}: refund required",
                        result.paymentId(), booking.getId(), booking.getStatus());
            }
            return;
        }
        if (result.succeeded()) {
            bookingService.confirm(booking);
        } else {
            log.info("Payment {} failed ({}), compensating booking {}", result.paymentId(), result.failureReason(),
                    booking.getId());
            bookingService.release(booking, BookingStatus.CANCELLED);
        }
    }
}
