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
     * Independent integrity check used by tests and the stats endpoint: seats of an event that
     * appear in more than one live (held/confirmed) booking. Must always be 0.
     */
    @Query(value = """
            SELECT count(*) FROM (
                SELECT bs.seat_id
                FROM booking_seats bs
                JOIN bookings b ON b.id = bs.booking_id
                WHERE b.event_id = :eventId AND b.status NOT IN ('CANCELLED', 'EXPIRED')
                GROUP BY bs.seat_id
                HAVING count(*) > 1
            ) doubles
            """, nativeQuery = true)
    long countDoubleBookedSeats(@Param("eventId") Long eventId);
}
