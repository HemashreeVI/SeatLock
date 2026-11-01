package com.HemashreeVI.seatlock.booking;

import com.HemashreeVI.seatlock.event.Event;
import com.HemashreeVI.seatlock.seat.Seat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private Long totalCents;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany
    @JoinTable(
            name = "booking_seats",
            joinColumns = @JoinColumn(name = "booking_id"),
            inverseJoinColumns = @JoinColumn(name = "seat_id")
    )
    private Set<Seat> seats = new HashSet<>();

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}