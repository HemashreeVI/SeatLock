package com.HemashreeVI.seatlock.seat;

/** Published (inside a transaction) whenever seat statuses of an event change. */
public record SeatsChangedEvent(Long eventId) {
}
