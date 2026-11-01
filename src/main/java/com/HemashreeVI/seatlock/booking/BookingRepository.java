package com.HemashreeVI.seatlock.booking;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
}