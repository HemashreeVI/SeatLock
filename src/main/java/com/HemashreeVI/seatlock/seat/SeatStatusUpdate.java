package com.HemashreeVI.seatlock.seat;

/** Minimal seat state pushed to live subscribers. */
public record SeatStatusUpdate(Long id, SeatStatus status) {
}
