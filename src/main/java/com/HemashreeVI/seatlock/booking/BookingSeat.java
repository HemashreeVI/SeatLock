package com.HemashreeVI.seatlock.booking;

import com.HemashreeVI.seatlock.seat.Seat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Link between a booking and one of its seats.
 * "active" becomes false when the booking is cancelled or expires; a partial unique
 * index guarantees a seat is in at most one active booking.
 */
@Entity
@Table(name = "booking_seats")
@Getter
@Setter
@NoArgsConstructor
public class BookingSeat {

    @EmbeddedId
    private BookingSeatId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("bookingId")
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("seatId")
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Column(nullable = false)
    private boolean active = true;

    public BookingSeat(Booking booking, Seat seat) {
        this.id = new BookingSeatId(booking.getId(), seat.getId());
        this.booking = booking;
        this.seat = seat;
    }
}
