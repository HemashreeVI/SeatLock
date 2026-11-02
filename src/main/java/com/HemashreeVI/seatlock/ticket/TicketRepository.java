package com.HemashreeVI.seatlock.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("select t from Ticket t join fetch t.seat join fetch t.event where t.userId = :userId order by t.issuedAt desc, t.id")
    List<Ticket> findMine(@Param("userId") String userId);

    @Query("select t from Ticket t join fetch t.seat join fetch t.event where t.code = :code")
    Optional<Ticket> findByCode(@Param("code") String code);

    /**
     * The whole check-in in ONE statement: only a VALID ticket flips to USED. If two gates scan the
     * same ticket at the same instant, the database lets exactly one of these updates match.
     */
    @Modifying
    @Query(value = """
            UPDATE tickets SET status = 'USED', checked_in_at = now(), checked_in_by = :staff
            WHERE code = :code AND status = 'VALID'
            """, nativeQuery = true)
    int markUsed(@Param("code") String code, @Param("staff") String staff);

    long countByBookingId(Long bookingId);
}
