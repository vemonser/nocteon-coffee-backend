-- ============================================================
-- V117: Stamp shippedAt/deliveredAt on orders, add estimatedDeliveryAt
-- ============================================================
-- For SHIPPED orders: set shipped_at on the order row
-- For DELIVERED orders: set delivered_at on the order row
-- Add estimated_delivery_at column to orders table

-- Add the ETA column
ALTER TABLE orders ADD COLUMN IF NOT EXISTS estimated_delivery_at TIMESTAMPTZ;

-- Stamp shippedAt for SHIPPED orders
UPDATE orders
SET shipped_at = updated_at
WHERE status = 'SHIPPED'
  AND shipped_at IS NULL;

-- Stamp deliveredAt for DELIVERED orders
UPDATE orders
SET delivered_at = updated_at
WHERE status = 'DELIVERED'
  AND delivered_at IS NULL;