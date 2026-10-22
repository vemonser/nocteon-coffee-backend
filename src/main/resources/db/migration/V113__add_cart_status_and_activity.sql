-- Cart lifecycle: status, last activity, converted order reference
ALTER TABLE carts
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS converted_order_id BIGINT;

-- Index for abandoned-cart queries and dashboard filters
CREATE INDEX IF NOT EXISTS idx_carts_status ON carts (status);
CREATE INDEX IF NOT EXISTS idx_carts_last_activity_at ON carts (last_activity_at);
CREATE INDEX IF NOT EXISTS idx_carts_converted_order_id ON carts (converted_order_id);

-- Initialize last_activity_at from updated_at for existing rows
UPDATE carts SET last_activity_at = updated_at WHERE last_activity_at IS NULL;
