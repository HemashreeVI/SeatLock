package com.HemashreeVI.seatlock.payment;

public record PaymentResult(PaymentResponse payment, boolean replayed) {
}
