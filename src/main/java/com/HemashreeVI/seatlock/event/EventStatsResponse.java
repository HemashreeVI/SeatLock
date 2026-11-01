package com.HemashreeVI.seatlock.event;

import java.util.Map;

/**
 * @param doubleBookedSeats seats that were ever owned by two bookings at overlapping times (live,
 *                          cancelled and expired bookings all count). Computed independently of
 *                          the unique index, so it is a genuine check. Must always be 0.
 */
public record EventStatsResponse(Long eventId, long totalSeats, Map<String, Long> seatsByStatus,
                                 Map<String, Long> bookingsByStatus, long doubleBookedSeats) {
}
