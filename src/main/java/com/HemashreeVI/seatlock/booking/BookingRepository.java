package com.HemashreeVI.seatlock.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    /**
     * Locks a batch of expired holds. SKIP LOCKED means several app instances can run the
     * sweeper at the same time without blocking each other or processing the same booking.
     */
    @Query(value = """
            SELECT id FROM bookings
            WHERE status = 'HELD' AND expires_at < :now
            ORDER BY expires_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> lockExpiredHeldIds(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * Independent, durable integrity audit used by tests, the stats endpoint and the load test:
     * the number of seats of an event that were EVER owned by two bookings at the same time.
     * <p>
     * Every booking that ever contained the seat is considered, including cancelled and expired
     * ones. A booking owns its seats from created_at until it ends: updated_at for a booking in a
     * final released state (CANCELLED / EXPIRED, never updated afterwards), open-ended otherwise.
     * Two ownership intervals of the same seat must never overlap. It doesn't rely on the
     * "active" flag or the unique index it is meant to verify. Must always be 0.
     */
    @Query(value = """
            SELECT count(DISTINCT a.seat_id)
            FROM booking_seats a
            JOIN booking_seats b ON b.seat_id = a.seat_id AND b.booking_id > a.booking_id
            JOIN bookings ba ON ba.id = a.booking_id
            JOIN bookings bb ON bb.id = b.booking_id
            WHERE ba.event_id = :eventId
              AND ba.created_at < CASE WHEN bb.status IN ('CANCELLED', 'EXPIRED') THEN bb.updated_at ELSE 'infinity'::timestamptz END
              AND bb.created_at < CASE WHEN ba.status IN ('CANCELLED', 'EXPIRED') THEN ba.updated_at ELSE 'infinity'::timestamptz END
            """, nativeQuery = true)
    long countDoubleBookedSeats(@Param("eventId") Long eventId);
}
