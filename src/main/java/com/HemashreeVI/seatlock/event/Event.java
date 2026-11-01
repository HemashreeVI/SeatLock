package com.HemashreeVI.seatlock.event;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String venue;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Hot event: users must pass the virtual waiting room before holding seats. */
    @Column(nullable = false)
    private boolean queueEnabled;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}