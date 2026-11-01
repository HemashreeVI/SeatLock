package com.HemashreeVI.seatlock.messaging;

import java.time.Instant;
import java.util.List;

/** JSON payloads exchanged over Kafka. */
public final class Messages {

    private Messages() {
    }

    public record PaymentRequested(long paymentId, long bookingId, long amountCents,
                                   String paymentToken, String idempotencyKey) {
    }

    public record PaymentCompleted(long paymentId, long bookingId, boolean succeeded, String failureReason) {
    }

    public record BookingEvent(String type, long bookingId, long eventId, String userId, String status,
                               long totalCents, List<Long> seatIds, Instant occurredAt) {
    }
}
