package com.HemashreeVI.seatlock.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A token from the payment provider's checkout widget (never raw card numbers). */
public record PayRequest(@NotBlank @Size(max = 100) String paymentToken) {
}
