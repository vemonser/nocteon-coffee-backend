-- Add status column to contact_messages
ALTER TABLE contact_messages
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'NEW';

-- Update existing rows based on is_replied
UPDATE contact_messages SET status = 'REPLIED' WHERE is_replied = true;
UPDATE contact_messages SET status = 'NEW' WHERE is_replied = false AND status = 'NEW';