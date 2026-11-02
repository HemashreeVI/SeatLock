-- ============================================================
-- Accounts (login) and e-tickets
-- ============================================================
CREATE TABLE users (
    id             BIGSERIAL    PRIMARY KEY,
    email          VARCHAR(254) NOT NULL,
    name           VARCHAR(100) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,          -- BCrypt, never the password itself
    role           VARCHAR(20)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- One ticket per seat of a confirmed booking. The code is a long random value: unguessable, so a
-- ticket can't be forged, and it is looked up server-side on every scan.
CREATE TABLE tickets (
    id               BIGSERIAL    PRIMARY KEY,
    booking_id       BIGINT       NOT NULL REFERENCES bookings(id),
    seat_id          BIGINT       NOT NULL REFERENCES seats(id),
    event_id         BIGINT       NOT NULL REFERENCES events(id),
    user_id          VARCHAR(100) NOT NULL,
    holder_name      VARCHAR(100),
    code             VARCHAR(64)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'VALID' CHECK (status IN ('VALID', 'USED')),
    issued_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    checked_in_at    TIMESTAMPTZ,
    checked_in_by    VARCHAR(100),
    CONSTRAINT uq_tickets_code UNIQUE (code),
    CONSTRAINT uq_tickets_booking_seat UNIQUE (booking_id, seat_id)   -- issuing twice is impossible
);
CREATE INDEX ix_tickets_user ON tickets (user_id, issued_at DESC);
CREATE INDEX ix_bookings_user ON bookings (user_id, created_at DESC);
