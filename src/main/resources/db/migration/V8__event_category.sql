-- Category shown on the event cards and used by the home page filters.
ALTER TABLE events ADD COLUMN category VARCHAR(30) NOT NULL DEFAULT 'OTHER';
