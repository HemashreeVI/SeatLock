package com.HemashreeVI.seatlock.booking;

public enum BookingStatus {
    /** Seats are reserved for a limited time, waiting for payment. */
    HELD,
    /** Payment requested; waiting for the asynchronous result. Not swept by the expiry job. */
    PAYMENT_PENDING,
    /** Paid; seats are BOOKED. */
    CONFIRMED,
    /** Cancelled by the user or by a failed payment; seats released. */
    CANCELLED,
    /** The hold timed out; seats released. */
    EXPIRED
}
