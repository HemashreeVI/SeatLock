-- ============================================================
-- Stage 2: seat holds, optimistic locking, idempotency, payments
-- ============================================================

-- ---------- seats: can now be HELD by a booking until a deadline ----------
ALTER TABLE seats DROP CONSTRAINT seats_status_check;
ALTER TABLE seats ADD CONSTRAINT ck_seats_status CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED'));

ALTER TABLE seats ADD COLUMN booking_id      BIGINT REFERENCES bookings(id);
ALTER TABLE seats ADD COLUMN hold_expires_at TIMESTAMPTZ;
-- optimistic locking: every UPDATE checks and bumps this number
ALTER TABLE seats ADD COLUMN version         BIGINT NOT NULL DEFAULT 0;

-- a seat is AVAILABLE if and only if nobody owns it
ALTER TABLE seats ADD CONSTRAINT ck_seats_owner CHECK (
    (status = 'AVAILABLE' AND booking_id IS NULL     AND hold_expires_at IS NULL) OR
    (status = 'HELD'      AND booking_id IS NOT NULL AND hold_expires_at IS NOT NULL) OR
    (status = 'BOOKED'    AND booking_id IS NOT NULL)
);

-- ---------- bookings: hold lifecycle + idempotency ----------
ALTER TABLE bookings DROP CONSTRAINT bookings_status_check;
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_status
    CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'EXPIRED'));

ALTER TABLE bookings ADD COLUMN expires_at      TIMESTAMPTZ;
ALTER TABLE bookings ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE bookings ADD COLUMN updated_at      TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE bookings ADD COLUMN version         BIGINT      NOT NULL DEFAULT 0;
UPDATE bookings SET expires_at = created_at WHERE expires_at IS NULL;
ALTER TABLE bookings ALTER COLUMN expires_at SET NOT NULL;

-- a client retry (same user + same Idempotency-Key) always maps to the same booking
CREATE UNIQUE INDEX uq_bookings_user_idempotency ON bookings (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
-- the expiry sweeper only ever looks at HELD bookings ordered by deadline
CREATE INDEX ix_bookings_held_expiry ON bookings (expires_at) WHERE status = 'HELD';

-- ---------- booking_seats: LAST LINE OF DEFENCE ----------
ALTER TABLE booking_seats ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
-- whatever the application does, a seat can belong to at most ONE active booking
CREATE UNIQUE INDEX uq_booking_seats_one_active ON booking_seats (seat_id) WHERE active;

-- ---------- payments: idempotent charges ----------
CREATE TABLE payments (
    id               BIGSERIAL    PRIMARY KEY,
    booking_id       BIGINT       NOT NULL REFERENCES bookings(id),
    idempotency_key  VARCHAR(100) NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL,
    amount_cents     BIGINT       NOT NULL CHECK (amount_cents >= 0),
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    provider_ref     VARCHAR(100),
    failure_reason   VARCHAR(200),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_idempotency UNIQUE (idempotency_key)
);
-- a booking can be charged successfully at most once, even with different keys
CREATE UNIQUE INDEX uq_payments_one_success ON payments (booking_id) WHERE status = 'SUCCEEDED';
CREATE INDEX ix_payments_booking ON payments (booking_id);
