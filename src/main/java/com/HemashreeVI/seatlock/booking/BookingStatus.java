package com.HemashreeVI.seatlock.booking;

public enum BookingStatus {
    /** Seats are reserved for a limited time, waiting for payment. */
    HELD,
    /** Paid; seats are BOOKED. */
    CONFIRMED,
    /** Cancelled by the user or by a failed payment; seats released. */
    CANCELLED,
    /** The hold timed out; seats released. */
    EXPIRED
}
