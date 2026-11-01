-- ============================================================
-- Stage 3: transactional outbox, payment saga, notifications
-- ============================================================

-- a booking waiting for the (asynchronous) payment result is no longer swept by the expiry job
ALTER TABLE bookings DROP CONSTRAINT ck_bookings_status;
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_status
    CHECK (status IN ('HELD', 'PAYMENT_PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED'));

-- Transactional outbox: events are written in the SAME transaction as the business change,
-- then a relay publishes them to Kafka. No "saved to DB but message lost" (or vice versa).
CREATE TABLE outbox_events (
    id              BIGSERIAL    PRIMARY KEY,
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,   -- also the Kafka message key (keeps per-booking ordering)
    event_type      VARCHAR(100) NOT NULL,
    topic           VARCHAR(200) NOT NULL,
    payload         TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(500)
);
CREATE INDEX ix_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;

-- Inbox / de-duplication: Kafka delivers at-least-once, so consumers remember what they handled
CREATE TABLE processed_messages (
    consumer      VARCHAR(100) NOT NULL,
    message_id    VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);

-- What the notification service "sent" (stand-in for emails / push)
CREATE TABLE notifications (
    id          BIGSERIAL    PRIMARY KEY,
    booking_id  BIGINT       NOT NULL,
    user_id     VARCHAR(100) NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
