-- V122: rebuild notifications for per-user in-app notifications (Z1)
-- Rollback: restore title/message/link/is_read columns and drop recipient_user_id/params/entity_type/entity_id/read_at.

-- Drop legacy global columns (keep updated_at for BaseEntity auditing).
ALTER TABLE notifications DROP COLUMN IF EXISTS title;
ALTER TABLE notifications DROP COLUMN IF EXISTS message;
ALTER TABLE notifications DROP COLUMN IF EXISTS link;
ALTER TABLE notifications DROP COLUMN IF EXISTS is_read;

-- Add per-user columns.
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS recipient_user_id BIGINT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS params TEXT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS entity_type VARCHAR(64);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS entity_id BIGINT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS read_at TIMESTAMP;

-- Remove legacy rows that have no recipient.
DELETE FROM notifications WHERE recipient_user_id IS NULL;

ALTER TABLE notifications ALTER COLUMN recipient_user_id SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN type TYPE VARCHAR(64);

-- Idempotency unique key.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_notifications_recipient_type_entity') THEN
        ALTER TABLE notifications
            ADD CONSTRAINT uq_notifications_recipient_type_entity
            UNIQUE (recipient_user_id, type, entity_type, entity_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read_created
    ON notifications (recipient_user_id, read_at, created_at DESC);
