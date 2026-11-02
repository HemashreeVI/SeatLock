package com.HemashreeVI.seatlock.ticket;

import java.time.Instant;

/** What a ticket holder (or the check-in staff) sees. */
public record TicketView(Long id, Long bookingId, String code, TicketStatus status, String eventName, String venue,
                         Instant startsAt, String seat, String holderName, Instant issuedAt, Instant checkedInAt) {

    static TicketView of(Ticket t) {
        return new TicketView(t.getId(), t.getBookingId(), t.getCode(), t.getStatus(), t.getEvent().getName(),
                t.getEvent().getVenue(), t.getEvent().getStartsAt(), t.seatLabel(), t.getHolderName(),
                t.getIssuedAt(), t.getCheckedInAt());
    }
}
