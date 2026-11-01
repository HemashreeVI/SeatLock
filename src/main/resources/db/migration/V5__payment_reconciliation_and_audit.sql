-- ============================================================
-- Review follow-ups: payment reconciliation + overlap audit
-- ============================================================

-- The provider token is kept on the payment so a stuck payment can be re-driven by the
-- reconciliation job (same idempotency key + same token => the provider never charges twice).
ALTER TABLE payments ADD COLUMN payment_token VARCHAR(100);
CREATE INDEX ix_payments_pending ON payments (created_at) WHERE status = 'PENDING';

-- Supports the durable double-booking audit, which looks at ALL bookings of a seat
-- (including cancelled / expired ones), not only the currently active one.
CREATE INDEX ix_booking_seats_seat ON booking_seats (seat_id);
