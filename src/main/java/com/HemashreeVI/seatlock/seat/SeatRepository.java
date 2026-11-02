package com.HemashreeVI.seatlock.seat;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventIdOrderBySectionAscRowLabelAscSeatNumberAsc(Long eventId);

    /** Just id + status for every seat of an event (the live-update payload). */
    @Query("select new com.HemashreeVI.seatlock.seat.SeatStatusUpdate(s.id, s.status) from Seat s where s.event.id = :eventId order by s.id")
    List<SeatStatusUpdate> findStatuses(@Param("eventId") Long eventId);

    List<Seat> findByEventIdAndStatus(Long eventId, SeatStatus status);

    /** Plain read, ordered by id. */
    @Query("select s from Seat s where s.id in :ids order by s.id")
    List<Seat> findAllByIdOrdered(@Param("ids") Collection<Long> ids);

    /**
     * SELECT ... FOR UPDATE: other transactions trying to lock the same rows wait until we commit.
     * Rows are locked in id order so two multi-seat bookings can never deadlock each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id in :ids order by s.id")
    List<Seat> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Query("select count(s) from Seat s where s.status = :status and s.event.startsAt > :after")
    long countByStatusInEventsStartingAfter(@Param("status") SeatStatus status, @Param("after") java.time.Instant after);

    /** One row per event: event id, total seats, available seats, cheapest and dearest seat. */
    @Query("""
            select s.event.id, count(s),
                   sum(case when s.status = com.HemashreeVI.seatlock.seat.SeatStatus.AVAILABLE then 1 else 0 end),
                   min(s.priceCents), max(s.priceCents)
            from Seat s group by s.event.id
            """)
    List<Object[]> summarizeAll();

    @Query("select s.status, count(s) from Seat s where s.event.id = :eventId group by s.status")
    List<Object[]> countByStatus(@Param("eventId") Long eventId);
}
