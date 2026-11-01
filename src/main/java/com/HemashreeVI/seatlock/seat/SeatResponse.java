package com.HemashreeVI.seatlock.seat;

public record SeatResponse(Long id, String section, String rowLabel, Integer seatNumber,
                           Long priceCents, SeatStatus status) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(seat.getId(), seat.getSection(), seat.getRowLabel(),
                seat.getSeatNumber(), seat.getPriceCents(), seat.getStatus());
    }
}
