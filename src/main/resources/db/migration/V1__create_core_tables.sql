-- Events: a concert, match, show...
CREATE TABLE events (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    venue       VARCHAR(200) NOT NULL,
    starts_at   TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Seats: every individual seat of an event
CREATE TABLE seats (
    id           BIGSERIAL   PRIMARY KEY,
    event_id     BIGINT      NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    section      VARCHAR(50) NOT NULL,
    row_label    VARCHAR(10) NOT NULL,
    seat_number  INT         NOT NULL,
    price_cents  BIGINT      NOT NULL CHECK (price_cents >= 0),
    status       VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                 CHECK (status IN ('AVAILABLE', 'BOOKED')),
    CONSTRAINT uq_seat_position UNIQUE (event_id, section, row_label, seat_number)
);
CREATE INDEX ix_seats_event_status ON seats (event_id, status);

-- Bookings: one user's purchase
CREATE TABLE bookings (
    id           BIGSERIAL    PRIMARY KEY,
    event_id     BIGINT       NOT NULL REFERENCES events(id),
    user_id      VARCHAR(100) NOT NULL,
    status       VARCHAR(20)  NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    total_cents  BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Which seats belong to which booking (one booking can have many seats)
CREATE TABLE booking_seats (
    booking_id  BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    seat_id     BIGINT NOT NULL REFERENCES seats(id),
    PRIMARY KEY (booking_id, seat_id)
);