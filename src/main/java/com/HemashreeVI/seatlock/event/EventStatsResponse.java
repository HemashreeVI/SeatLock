package com.HemashreeVI.seatlock.event;

import java.util.Map;

/**
 * @param doubleBookedSeats seats appearing in more than one live booking. Computed independently
 *                          of the unique index, so it is a genuine check. Must always be 0.
 */
public record EventStatsResponse(Long eventId, long totalSeats, Map<String, Long> seatsByStatus,
                                 Map<String, Long> bookingsByStatus, long doubleBookedSeats) {
}
