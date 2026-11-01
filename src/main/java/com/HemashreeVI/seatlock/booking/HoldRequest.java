package com.HemashreeVI.seatlock.booking;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * JSON body for POST /api/bookings.
 *
 * @param name  optional customer name, used in notification emails
 * @param email optional address that receives confirmation / cancellation / expiry emails
 */
public record HoldRequest(
        @NotNull Long eventId,
        @NotEmpty List<@NotNull Long> seatIds,
        @Size(max = 100) String name,
        @Email @Size(max = 254) String email
) {
    public HoldRequest(Long eventId, List<Long> seatIds) {
        this(eventId, seatIds, null, null);
    }

    String normalizedName() {
        return name == null || name.isBlank() ? null : name.strip();
    }

    String normalizedEmail() {
        return email == null || email.isBlank() ? null : email.strip().toLowerCase();
    }
}
