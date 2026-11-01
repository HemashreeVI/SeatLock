package com.HemashreeVI.seatlock.seat;

import com.HemashreeVI.seatlock.event.Event;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}