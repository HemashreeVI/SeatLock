package com.HemashreeVI.seatlock.payment;

/** The external payment provider (Stripe, Razorpay, ...). */
public interface PaymentGateway {

    /**
     * Charges the customer. Real providers accept an idempotency key too, so a retried
     * call never charges twice.
     */
    ChargeResult charge(String idempotencyKey, long amountCents, String paymentToken);

    record ChargeResult(boolean success, String providerRef, String failureReason) {
    }
}
