-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

-- ============================================================
-- V116: Extend seed orders to cover 75 days for dashboard delta
-- ============================================================
-- This migration spreads the existing seed orders over 75 days so that
-- the dashboard's previous 30-day period has meaningful data for delta
-- calculations. It also ensures order numbers increase with creation time.
-- ============================================================

-- 1. First, fix the order numbers to be chronologically assigned
--    Newest order = highest number
UPDATE orders 
SET order_number = 'ORD-2026-' || LPAD(id::text, 6, '0')
WHERE order_number LIKE 'ORD-2026-%';

-- 2. Spread orders over 75 days (newest = highest number, oldest = lowest)
--    Using a deterministic distribution based on order_number
UPDATE orders
SET created_at = NOW() - (
  CASE 
    -- Distribute 17 orders over 75 days with realistic gaps
    WHEN order_number = 'ORD-2026-000001' THEN interval '74 days'
    WHEN order_number = 'ORD-2026-000002' THEN interval '72 days'
    WHEN order_number = 'ORD-2026-000003' THEN interval '68 days'
    WHEN order_number = 'ORD-2026-000004' THEN interval '65 days'
    WHEN order_number = 'ORD-2026-000005' THEN interval '60 days'
    WHEN order_number = 'ORD-2026-000006' THEN interval '57 days'
    WHEN order_number = 'ORD-2026-000007' THEN interval '52 days'
    WHEN order_number = 'ORD-2026-000008' THEN interval '48 days'
    WHEN order_number = 'ORD-2026-000009' THEN interval '45 days'
    WHEN order_number = 'ORD-2026-000010' THEN interval '40 days'
    WHEN order_number = 'ORD-2026-000011' THEN interval '37 days'
    WHEN order_number = 'ORD-2026-000012' THEN interval '33 days'
    WHEN order_number = 'ORD-2026-000013' THEN interval '28 days'
    WHEN order_number = 'ORD-2026-000014' THEN interval '25 days'
    WHEN order_number = 'ORD-2026-000015' THEN interval '20 days'
    WHEN order_number = 'ORD-2026-000016' THEN interval '15 days'
    WHEN order_number = 'ORD-2026-000017' THEN interval '10 days'
    ELSE interval '5 days'
  END
),
updated_at = created_at
WHERE order_number LIKE 'ORD-2026-%';

-- 3. Add additional orders to cover 75 days with realistic spread
--    Insert 25 more orders spanning the 75-day period
INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
SELECT 
  CASE WHEN n % 5 = 0 THEN 1 ELSE (n % 5) + 1 END,
  CASE WHEN n % 5 = 0 THEN 1 ELSE (n % 5) + 1 END,
  'ORD-2026-' || LPAD((17 + n)::text, 6, '0'),
  CASE 
    WHEN n % 10 = 0 THEN 'PROCESSING'
    WHEN n % 7 = 0 THEN 'SHIPPED'
    WHEN n % 4 = 0 THEN 'CANCELLED'
    WHEN n % 3 = 0 THEN 'PENDING'
    ELSE 'DELIVERED'
  END,
  CASE 
    WHEN n % 4 = 0 THEN 'FAILED'
    WHEN n % 3 = 0 THEN 'PENDING'
    WHEN n % 10 = 0 THEN 'REFUNDED'
    ELSE 'PAID'
  END,
  CASE WHEN n % 2 = 0 THEN 'ONLINE' ELSE 'CASH_ON_DELIVERY' END,
  50.00,
  (50 + (n * 15))::numeric,
  0.00,
  'Extended seed order ' || n,
  NOW() - (75 - n)::int * INTERVAL '1 day',
  NOW() - (75 - n)::int * INTERVAL '1 day'
FROM generate_series(1, 25) AS n
WHERE NOT EXISTS (
  SELECT 1 FROM orders 
  WHERE order_number = 'ORD-2026-' || LPAD((17 + n)::text, 6, '0')
);

-- 4. Ensure 2 refunded orders in current period (days 0-30)
UPDATE orders 
SET payment_status = 'REFUNDED', status = 'DELIVERED'
WHERE order_number IN ('ORD-2026-000009', 'ORD-2026-000012')
  AND created_at >= NOW() - interval '30 days';

-- 5. Ensure at least 1 order in Processing and 1 in Shipped
UPDATE orders 
SET status = 'PROCESSING', payment_status = 'PAID'
WHERE order_number = 'ORD-2026-000013';

UPDATE orders 
SET status = 'SHIPPED', payment_status = 'PAID'
WHERE order_number = 'ORD-2026-000008';

-- 6. Ensure repeat customer (user_id 1 appears in multiple orders)
--    Already satisfied by existing seed data

-- 7. Recompute all seeded order totals from their items
UPDATE orders o
SET total_amount = COALESCE(
  (SELECT SUM(oi.total_price) FROM order_items oi WHERE oi.order_id = o.id),
  0
) - COALESCE(o.discount_amount, 0) + o.shipping_cost
WHERE o.order_number LIKE 'ORD-2026-%';

-- 8. Re-date the status history rows from the (new) created_at
UPDATE order_status_history h
SET created_at = o.created_at + CASE h.to_status
  WHEN 'CONFIRMED' THEN interval '1 hour'
  WHEN 'PROCESSING' THEN interval '2 hours'
  WHEN 'SHIPPED' THEN interval '1 day'
  WHEN 'DELIVERED' THEN interval '3 days'
  WHEN 'CANCELLED' THEN interval '2 hours'
  ELSE interval '0'
END
FROM orders o
WHERE h.order_id = o.id 
  AND o.order_number LIKE 'ORD-2026-%' 
  AND h.from_status IS NOT NULL;

UPDATE order_status_history h
SET created_at = o.created_at
FROM orders o
WHERE h.order_id = o.id 
  AND o.order_number LIKE 'ORD-2026-%' 
  AND h.from_status IS NULL;

-- 9. Re-stamp shipment dates from the order's created_at
UPDATE orders 
SET shipped_at = created_at + interval '1 day', 
    delivered_at = created_at + interval '3 days'
WHERE status IN ('SHIPPED', 'DELIVERED') 
  AND order_number LIKE 'ORD-2026-%';

-- 10. Ensure payments exist for all PAID orders
INSERT INTO payments (order_id, provider, provider_payment_id, amount, currency, status, payment_method, attempt_number, paid_at, created_at, updated_at)
SELECT o.id, 'PAYMOB', 'PAYMOB-' || o.id, o.total_amount, 'EGP', 'PAID', 'ONLINE', 1, o.updated_at, o.created_at, o.updated_at
FROM orders o
WHERE o.payment_status = 'PAID'
  AND o.payment_method = 'ONLINE'
  AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id);

INSERT INTO payments (order_id, provider, provider_payment_id, amount, currency, status, payment_method, attempt_number, paid_at, created_at, updated_at)
SELECT o.id, 'COD', 'COD', o.total_amount, 'EGP', 'PAID', 'CASH_ON_DELIVERY', 1, o.updated_at, o.created_at, o.updated_at
FROM orders o
WHERE o.payment_status = 'PAID'
  AND o.payment_method = 'CASH_ON_DELIVERY'
  AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id);

-- 11. Add order items for new orders
INSERT INTO order_items (order_id, product_variant_id, quantity, unit_price, total_price)
SELECT o.id, 1, 1, 500.00, 500.00 
FROM orders o 
WHERE o.order_number LIKE 'ORD-2026-%' 
  AND o.order_number > 'ORD-2026-000017'
  AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.id);

-- 12. Recompute totals for new orders
UPDATE orders o
SET total_amount = COALESCE(
  (SELECT SUM(oi.total_price) FROM order_items oi WHERE oi.order_id = o.id),
  0
) - COALESCE(o.discount_amount, 0) + o.shipping_cost
WHERE o.order_number LIKE 'ORD-2026-%' 
  AND o.order_number > 'ORD-2026-000017';
