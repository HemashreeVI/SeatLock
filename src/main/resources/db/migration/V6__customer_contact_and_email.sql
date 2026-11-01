-- ============================================================
-- Customer contact details on bookings + email delivery status
-- ============================================================
ALTER TABLE bookings ADD COLUMN customer_name  VARCHAR(100);
ALTER TABLE bookings ADD COLUMN customer_email VARCHAR(254);

ALTER TABLE notifications ADD COLUMN email_to     VARCHAR(254);
-- NONE (no address given), PENDING, SENT, FAILED, SKIPPED (daily cap reached)
ALTER TABLE notifications ADD COLUMN email_status VARCHAR(20) NOT NULL DEFAULT 'NONE';
CREATE INDEX ix_notifications_email_to ON notifications (email_to, created_at) WHERE email_to IS NOT NULL;
