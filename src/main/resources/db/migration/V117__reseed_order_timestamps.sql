-- V117: reseed order timestamps so every order satisfies
--   createdAt <= paidAt <= shippedAt <= deliveredAt <= now
-- and order numbers increase with createdAt (oldest = 000001).

-- 1. Spread the 17 orders over the last 75 days (deterministic offsets).
WITH ordered AS (
  SELECT id, row_number() OVER (ORDER BY id) AS rn
  FROM orders WHERE order_number LIKE 'ORD-2026-%'
),
offs AS (
  SELECT unnest(ARRAY[74,70,66,62,57,53,48,44,39,35,30,26,21,16,11,6,2]) AS days_ago,
         generate_series(1, 17) AS rn
)
UPDATE orders o
SET created_at = now() - (off.days_ago::text || ' days')::interval,
    updated_at = now() - (off.days_ago::text || ' days')::interval
FROM ordered ord JOIN offs off ON off.rn = ord.rn
WHERE o.id = ord.id;

-- 2. Derived milestone timestamps.
UPDATE orders
SET shipped_at   = created_at + interval '8 hours',
    delivered_at = created_at + interval '30 hours'
WHERE status IN ('SHIPPED','DELIVERED');

-- 3. Payments: online paidAt = createdAt + 1h; COD paidAt = deliveredAt.
UPDATE payments p
SET paid_at = o.created_at + interval '1 hour',
    updated_at = o.created_at + interval '1 hour'
FROM orders o
WHERE p.order_id = o.id
  AND o.payment_method = 'ONLINE'
  AND p.status = 'PAID';

UPDATE payments p
SET paid_at = o.delivered_at,
    updated_at = o.delivered_at
FROM orders o
WHERE p.order_id = o.id
  AND o.payment_method = 'CASH_ON_DELIVERY'
  AND p.status = 'PAID';

-- refunded payments keep their paid_at (already set) and refunds land +6h after delivery
UPDATE order_refunds r
SET created_at = o.delivered_at + interval '6 hours'
FROM orders o
WHERE r.order_id = o.id
  AND o.status = 'DELIVERED'
  AND o.payment_status = 'REFUNDED';

-- 4. Status history timestamps.
UPDATE order_status_history h
SET created_at = o.created_at
FROM orders o
WHERE h.order_id = o.id AND h.from_status IS NULL;

UPDATE order_status_history h
SET created_at = o.created_at + interval '1 hour'
FROM orders o
WHERE h.order_id = o.id AND h.from_status = 'PENDING' AND h.to_status = 'CONFIRMED';

UPDATE order_status_history h
SET created_at = o.created_at + interval '2 hours'
FROM orders o
WHERE h.order_id = o.id AND h.from_status = 'CONFIRMED' AND h.to_status = 'PROCESSING';

UPDATE order_status_history h
SET created_at = o.created_at + interval '8 hours'
FROM orders o
WHERE h.order_id = o.id AND h.from_status = 'PROCESSING' AND h.to_status = 'SHIPPED';

UPDATE order_status_history h
SET created_at = o.created_at + interval '30 hours'
FROM orders o
WHERE h.order_id = o.id AND h.from_status = 'SHIPPED' AND h.to_status = 'DELIVERED';

UPDATE order_status_history h
SET created_at = o.created_at + interval '2 hours'
FROM orders o
WHERE h.order_id = o.id AND h.to_status = 'CANCELLED';

-- 5. Re-number orders by createdAt ASC.
WITH ranked AS (
  SELECT id, row_number() OVER (ORDER BY created_at ASC, id ASC) AS rn
  FROM orders WHERE order_number LIKE 'ORD-2026-%'
)
UPDATE orders o
SET order_number = 'ORD-2026-' || LPAD(r.rn::text, 6, '0')
FROM ranked r
WHERE o.id = r.id;

-- 6. Startup assertion: throw if any order violates the invariant.
DO $$
DECLARE
  v_count integer;
  v_bad integer;
BEGIN
  SELECT count(*) INTO v_count FROM orders WHERE order_number LIKE 'ORD-2026-%';
  SELECT count(*) INTO v_bad
  FROM orders o
  LEFT JOIN payments p ON p.order_id = o.id
  WHERE o.order_number LIKE 'ORD-2026-%'
    AND NOT (
         o.created_at <= COALESCE(p.paid_at, o.created_at)
     AND (p.paid_at IS NULL OR o.shipped_at IS NULL OR p.paid_at <= o.shipped_at)
     AND (o.shipped_at IS NULL OR o.delivered_at IS NULL OR o.shipped_at <= o.delivered_at)
     AND COALESCE(o.delivered_at, o.created_at) <= now()
     AND o.created_at <= now()
    );
  IF v_bad > 0 THEN
    RAISE EXCEPTION 'Seed invariant violated for % of % orders', v_bad, v_count;
  END IF;
  RAISE NOTICE 'Seed check passed: % orders verified', v_count;
END $$;
