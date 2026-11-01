package com.HemashreeVI.seatlock.booking;

import com.HemashreeVI.seatlock.common.ConflictException;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** One or more requested seats are already taken (or being taken right now). HTTP 409. */
public class SeatUnavailableException extends ConflictException {

    private final List<Long> seatIds;

    public SeatUnavailableException(Collection<Long> seatIds, String message) {
        super(message, Map.of("seatIds", List.copyOf(seatIds)));
        this.seatIds = List.copyOf(seatIds);
    }

    public List<Long> getSeatIds() {
        return seatIds;
    }
}
