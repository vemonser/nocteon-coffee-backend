-- ============================================================
-- V111: Repair the totals of the seeded orders
-- ============================================================
--
-- The seed (V107) wrote `orders.total_amount` by hand, independently of the
-- `order_items` rows it inserted in the same file. Every seeded order
-- therefore disagrees with its own items:
--
--   ORD-2026-000001  stores 120.50  while its items are 1,825.00 + 50.00
--   ORD-2026-000002  stores  85.00  while its items are 1,889.00 + 50.00
--   ... and so on for all seventeen.
--
-- The RUNTIME formula in `OrderService.createOrder` is correct:
--
--     total = (sum of the line totals - discount) + shipping
--
-- so this migration recomputes the stored total with exactly that formula
-- and touches only the rows that disagree. A row that already satisfies the
-- formula is left alone, and a row the application created (whose total was
-- computed by that formula) is therefore never rewritten.
--
-- The seventeen seeded order numbers are listed EXPLICITLY on purpose: the
-- repair is a data fix for the seed, not a rule for the store's real
-- orders. An order the application creates later must keep the total it
-- was given — the store's books are not this migration's to rewrite.
--
-- The orders with no items are repaired too: their total becomes the
-- shipping, less the discount — which is what the formula says they cost.

UPDATE orders o
SET total_amount = (
        SELECT COALESCE(SUM(oi.total_price), 0)
                   - COALESCE(o.discount_amount, 0)
                   + o.shipping_cost
        FROM order_items oi
        WHERE oi.order_id = o.id
    )
WHERE o.order_number IN (
        'ORD-2026-000001', 'ORD-2026-000002', 'ORD-2026-000003',
        'ORD-2026-000004', 'ORD-2026-000005', 'ORD-2026-000006',
        'ORD-2026-000007', 'ORD-2026-000008', 'ORD-2026-000009',
        'ORD-2026-000010', 'ORD-2026-000011', 'ORD-2026-000012',
        'ORD-2026-000013', 'ORD-2026-000014', 'ORD-2026-000015',
        'ORD-2026-000016', 'ORD-2026-000017'
    )
  AND o.total_amount <> (
        SELECT COALESCE(SUM(oi.total_price), 0)
                   - COALESCE(o.discount_amount, 0)
                   + o.shipping_cost
        FROM order_items oi
        WHERE oi.order_id = o.id
    );
