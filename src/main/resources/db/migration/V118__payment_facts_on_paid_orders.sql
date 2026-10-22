-- ============================================================
-- V118: Insert payment records and set paidAt/transactionReference
-- ============================================================
-- The seeded orders have no payment rows. Insert them for PAID orders.
-- For ONLINE: paid_at = updated_at, provider_payment_id = 'PAYMOB-' || order.id
-- For COD: paid_at = updated_at, provider_payment_id = 'COD'

-- ONLINE PAID orders
INSERT INTO payments (order_id, provider, provider_payment_id, amount, currency, status, payment_method, attempt_number, paid_at, created_at, updated_at)
SELECT o.id, 'PAYMOB', 'PAYMOB-' || o.id, o.total_amount, 'EGP', 'PAID', 'ONLINE', 1, o.updated_at, o.created_at, o.updated_at
FROM orders o
WHERE o.payment_status = 'PAID'
  AND o.payment_method = 'ONLINE'
  AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id);

-- COD PAID orders (order 6 is CANCELLED+NOT_COLLECTED, not PAID; no COD PAID in seed)
-- If any COD PAID orders exist in future:
-- INSERT INTO payments (order_id, provider, provider_payment_id, amount, currency, status, payment_method, attempt_number, paid_at, created_at, updated_at)
-- SELECT o.id, 'COD', 'COD', o.total_amount, 'EGP', 'PAID', 'CASH_ON_DELIVERY', 1, o.updated_at, o.created_at, o.updated_at
-- FROM orders o
-- WHERE o.payment_status = 'PAID'
--   AND o.payment_method = 'CASH_ON_DELIVERY'
--   AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id);