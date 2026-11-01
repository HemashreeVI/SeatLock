package com.HemashreeVI.seatlock.event;

import java.time.Instant;

public record EventResponse(Long id, String name, String venue, Instant startsAt, Instant createdAt) {

    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getName(), event.getVenue(),
                event.getStartsAt(), event.getCreatedAt());
    }
}
