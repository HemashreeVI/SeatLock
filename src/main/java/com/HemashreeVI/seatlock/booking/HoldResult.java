package com.HemashreeVI.seatlock.booking;

/** @param replayed true if an earlier request with the same Idempotency-Key already created it */
public record HoldResult(BookingResponse booking, boolean replayed) {
}
