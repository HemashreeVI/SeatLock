package com.HemashreeVI.seatlock.event;

import java.time.Instant;

/**
 * @param totalSeats     seats of the event (null when not computed)
 * @param availableSeats seats still free right now
 * @param minPriceCents  cheapest seat ("from ...")
 */
public record EventResponse(Long id, String name, String venue, Instant startsAt, Instant createdAt,
                            boolean queueEnabled, EventCategory category,
                            Long totalSeats, Long availableSeats, Long minPriceCents, Long maxPriceCents) {

    public static EventResponse from(Event event) {
        return from(event, null);
    }

    public static EventResponse from(Event event, EventSummary summary) {
        return new EventResponse(event.getId(), event.getName(), event.getVenue(),
                event.getStartsAt(), event.getCreatedAt(), event.isQueueEnabled(), event.getCategory(),
                summary == null ? null : summary.totalSeats(),
                summary == null ? null : summary.availableSeats(),
                summary == null ? null : summary.minPriceCents(),
                summary == null ? null : summary.maxPriceCents());
    }

    /** Seat counts and price range of one event. */
    public record EventSummary(long totalSeats, long availableSeats, long minPriceCents, long maxPriceCents) {
    }
}
