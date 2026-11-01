package com.HemashreeVI.seatlock.seat;

import com.HemashreeVI.seatlock.event.Event;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "seats")
@Getter
@Setter
@NoArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(nullable = false)
    private String section;

    @Column(nullable = false)
    private String rowLabel;

    @Column(nullable = false)
    private Integer seatNumber;

    @Column(nullable = false)
    private Long priceCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status = SeatStatus.AVAILABLE;

    /** The booking currently holding or owning this seat (null when AVAILABLE). */
    @Column(name = "booking_id")
    private Long bookingId;

    private Instant holdExpiresAt;

    /**
     * Optimistic locking: Hibernate adds "AND version = ?" to every UPDATE and bumps it.
     * If another transaction changed the seat in between, the update matches 0 rows and fails.
     */
    @Version
    private Long version;

    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE;
    }

    public void hold(Long bookingId, Instant until) {
        this.status = SeatStatus.HELD;
        this.bookingId = bookingId;
        this.holdExpiresAt = until;
    }

    public void book() {
        this.status = SeatStatus.BOOKED;
        this.holdExpiresAt = null;
    }

    public void release() {
        this.status = SeatStatus.AVAILABLE;
        this.bookingId = null;
        this.holdExpiresAt = null;
    }
}
