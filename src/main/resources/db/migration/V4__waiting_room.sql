-- ============================================================
-- Stage 4: per-event virtual waiting room
-- ============================================================
-- For hot events, users must queue and be admitted before they may hold seats.
ALTER TABLE events ADD COLUMN queue_enabled BOOLEAN NOT NULL DEFAULT FALSE;
