package com.HemashreeVI.seatlock.booking;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** JSON body for POST /api/bookings. */
public record HoldRequest(
        @NotNull Long eventId,
        @NotEmpty List<@NotNull Long> seatIds
) {
}
