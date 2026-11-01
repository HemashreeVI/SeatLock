package com.HemashreeVI.seatlock.seat;

public enum SeatStatus {
    /** Free to be held. */
    AVAILABLE,
    /** Temporarily reserved by a booking until hold_expires_at. */
    HELD,
    /** Paid for. */
    BOOKED
}
