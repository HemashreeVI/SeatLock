package com.HemashreeVI.seatlock.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, BookingSeatId> {

    @Query("select bs from BookingSeat bs join fetch bs.seat where bs.booking.id = :bookingId order by bs.seat.id")
    List<BookingSeat> findByBookingIdWithSeats(@Param("bookingId") Long bookingId);
}
