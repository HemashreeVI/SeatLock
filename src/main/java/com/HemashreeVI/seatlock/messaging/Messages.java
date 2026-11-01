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

    /**
     * eventName, seatLabels, customerName and customerEmail were added later; messages written by
     * older versions simply have them as null.
     */
    public record BookingEvent(String type, long bookingId, long eventId, String userId, String status,
                               long totalCents, List<Long> seatIds, Instant occurredAt,
                               String eventName, List<String> seatLabels,
                               String customerName, String customerEmail) {
    }
}
