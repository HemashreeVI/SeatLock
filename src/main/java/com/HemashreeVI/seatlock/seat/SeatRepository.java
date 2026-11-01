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

    @Query("select s.status, count(s) from Seat s where s.event.id = :eventId group by s.status")
    List<Object[]> countByStatus(@Param("eventId") Long eventId);
}
