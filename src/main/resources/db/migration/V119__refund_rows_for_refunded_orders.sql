-- ============================================================
-- V119: Insert refund rows for REFUNDED orders
-- ============================================================
-- Orders 9 and 12 have payment_status = REFUNDED but no refund rows.
-- Insert full-refund rows for them.

INSERT INTO order_refunds (order_id, amount, reason, created_by, created_at)
SELECT o.id, o.total_amount, 'Full refund (seed)', 'seed', o.updated_at
FROM orders o
WHERE o.payment_status = 'REFUNDED'
  AND NOT EXISTS (SELECT 1 FROM order_refunds r WHERE r.order_id = o.id);