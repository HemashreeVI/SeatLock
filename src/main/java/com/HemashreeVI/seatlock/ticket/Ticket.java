package com.HemashreeVI.seatlock.ticket;

import com.HemashreeVI.seatlock.event.Event;
import com.HemashreeVI.seatlock.seat.Seat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bookingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(nullable = false)
    private String userId;

    private String holderName;

    /** 128-bit random value (hex): unguessable, unique, what the QR code contains. */
    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.VALID;

    @Column(nullable = false, updatable = false)
    private Instant issuedAt;

    private Instant checkedInAt;

    private String checkedInBy;

    @PrePersist
    void onCreate() {
        this.issuedAt = Instant.now();
    }

    public String seatLabel() {
        return seat.getSection() + " " + seat.getRowLabel() + seat.getSeatNumber();
    }
}
