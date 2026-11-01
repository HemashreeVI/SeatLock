package com.HemashreeVI.seatlock.booking;

import com.HemashreeVI.seatlock.seat.Seat;
import com.HemashreeVI.seatlock.seat.SeatResponse;

import java.time.Instant;
import java.util.List;

public record BookingResponse(
        Long id,
        Long eventId,
        String userId,
        BookingStatus status,
        Long totalCents,
        Instant expiresAt,
        Instant createdAt,
        List<SeatResponse> seats,
        String customerName,
        String customerEmail
) {
    public static BookingResponse of(Booking booking, List<Seat> seats) {
        return new BookingResponse(booking.getId(), booking.getEvent().getId(), booking.getUserId(),
                booking.getStatus(), booking.getTotalCents(), booking.getExpiresAt(), booking.getCreatedAt(),
                seats.stream().map(SeatResponse::from).toList(), booking.getCustomerName(), booking.getCustomerEmail());
    }
}
