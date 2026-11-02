package com.HemashreeVI.seatlock.ticket;

import java.time.Instant;

/**
 * @param result  ADMITTED (let them in), ALREADY_USED (someone already entered with it) or INVALID
 * @param ticket  details for the staff screen; null when the code is unknown
 */
public record CheckInResult(Result result, String message, TicketView ticket, Instant checkedInAt) {

    public enum Result { ADMITTED, ALREADY_USED, INVALID }
}
