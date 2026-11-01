package com.HemashreeVI.seatlock.payment;

import java.time.Instant;

public record PaymentResponse(Long id, Long bookingId, PaymentStatus status, Long amountCents,
                              String providerRef, String failureReason, Instant createdAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getBookingId(), p.getStatus(), p.getAmountCents(),
                p.getProviderRef(), p.getFailureReason(), p.getCreatedAt());
    }
}
