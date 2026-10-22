-- Add status column to reviews
ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

-- Update existing rows - if approved is true, set to APPROVED, else PENDING
UPDATE reviews SET status = 'APPROVED' WHERE is_approved = true;
UPDATE reviews SET status = 'PENDING' WHERE is_approved = false;