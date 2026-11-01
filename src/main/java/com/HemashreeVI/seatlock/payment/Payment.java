package com.HemashreeVI.seatlock.payment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bookingId;

    /** Client-supplied key: the same key always means the same payment attempt. */
    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    /** Fingerprint of the request body, to detect a key reused for a different request. */
    @Column(nullable = false)
    private String requestHash;

    @Column(nullable = false)
    private Long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /** Provider checkout token; lets the reconciliation job re-drive a stuck payment. */
    private String paymentToken;

    private String providerRef;

    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
