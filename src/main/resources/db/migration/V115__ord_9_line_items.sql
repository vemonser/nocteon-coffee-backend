-- ============================================================
-- V115: Add line items and recompute total for ORD-9
-- ============================================================
-- ORD-2026-000009 (id=9) was seeded with no line items and total=0.00.
-- It is a DELIVERED, REFUNDED order that should have real items.
-- We add two line items and recompute the total with the store's formula.

INSERT INTO order_items (order_id, product_variant_id, quantity, unit_price, total_price)
SELECT * FROM (VALUES
    (9, 1, 1, 650.00, 650.00),
    (9, 2, 1, 689.00, 689.00)
) AS items(order_id, product_variant_id, quantity, unit_price, total_price)
WHERE NOT EXISTS (
    SELECT 1 FROM order_items oi
    WHERE oi.order_id = items.order_id
      AND oi.product_variant_id = items.product_variant_id
);

-- Recompute total: sum(items) - discount + shipping
-- discount=50, shipping=50 per seed
UPDATE orders o
SET total_amount = (
        SELECT COALESCE(SUM(oi.total_price), 0)
                   - COALESCE(o.discount_amount, 0)
                   + o.shipping_cost
        FROM order_items oi
        WHERE oi.order_id = o.id
    )
WHERE o.id = 9
  AND o.total_amount <> (
        SELECT COALESCE(SUM(oi.total_price), 0)
                   - COALESCE(o.discount_amount, 0)
                   + o.shipping_cost
        FROM order_items oi
        WHERE oi.order_id = o.id
    );