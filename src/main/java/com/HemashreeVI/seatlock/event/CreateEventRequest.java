package com.HemashreeVI.seatlock.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

/** JSON body for POST /api/events. Seats are generated from the sections. */
public record CreateEventRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 200) String venue,
        @NotNull @Future Instant startsAt,
        @NotEmpty @Size(max = 20) @Valid List<SectionRequest> sections,
        Boolean queueEnabled
) {
    public record SectionRequest(
            @NotBlank @Size(max = 50) String name,
            @Min(1) @Max(26) int rows,
            @Min(1) @Max(100) int seatsPerRow,
            @PositiveOrZero long priceCents
    ) {
    }
}
