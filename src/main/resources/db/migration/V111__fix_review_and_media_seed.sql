-- ============================================================
-- V111: Make the review seed coherent
-- ============================================================
-- The moderation console shows a check mark next to a reviewer's name
-- when the purchase is CONFIRMED, and the dashboard links that mark to
-- the order behind it. Three things in the seed made that a lie:
--
--   1. The seeded orders carried NO line items, so no review could
--      have a qualifying order at all.
--   2. Three of the fifteen reviews were written by user_id 1, the
--      ADMIN account — so the moderation queue showed a moderator
--      reviewing their own store's products.
--   3. Every review was marked verified, including the ones whose
--      buyer never bought the product.
--
-- Every count is unchanged: 15 reviews, 13 approved, 2 pending.

-- ----------------------------------------------------------
-- 1. The order line items
-- ----------------------------------------------------------
-- The orders existed but had no items, so the dashboard's revenue
-- aggregations had nothing to aggregate and no review could point at
-- an order. The insert is guarded so it is safe in a database that
-- already has them.
INSERT INTO order_items (order_id, product_variant_id, quantity, unit_price, total_price)
SELECT * FROM (VALUES
    (1,  1, 2,  650.00, 1300.00),
    (1,  4, 1,  525.00,  525.00),
    (2,  2, 1,  689.00,  689.00),
    (2,  7, 2,  600.00, 1200.00),
    (3,  3, 1, 1378.00, 1378.00),
    (3, 10, 2,  450.00,  900.00),
    (4,  5, 2,  556.50, 1113.00),
    (4,  8, 1,  636.00,  636.00),
    (5,  6, 1, 1113.00, 1113.00),
    (5,  9, 2, 1272.00, 2544.00),
    (8,  1, 1,  650.00,  650.00),
    (8, 11, 2,  477.00,  954.00),
    (11, 4, 1,  525.00,  525.00),
    (11,12, 1,  954.00,  954.00),
    (13, 7, 1,  600.00,  600.00),
    (13, 8, 1,  636.00,  636.00),
    (16, 2, 1,  689.00,  689.00),
    (16, 5, 1,  556.50,  556.50),
    (17, 3, 1, 1378.00, 1378.00),
    (17, 9, 2, 1272.00, 2544.00)
) AS items(order_id, product_variant_id, quantity, unit_price, total_price)
WHERE NOT EXISTS (
    SELECT 1 FROM order_items oi
    WHERE oi.order_id = items.order_id
      AND oi.product_variant_id = items.product_variant_id
);

-- ----------------------------------------------------------
-- 2. The three admin-authored reviews
-- ----------------------------------------------------------
-- They move to customer accounts that have no review for that
-- product yet — the table has a UNIQUE (user_id, product_id)
-- constraint — and, where possible, have actually bought it, so the
-- "verified purchase" check mark has an order behind it.
--
--   review 1  admin  -> mohamed (3)  ethiopia-yirgacheffe
--   review 6  admin  -> ahmed   (2)  kenya-nyeri
--   review 11 admin  -> youssef (7)  hario-v60
UPDATE reviews SET user_id = 3 WHERE id = 1 AND user_id = 1;
UPDATE reviews SET user_id = 2 WHERE id = 6 AND user_id = 1;
UPDATE reviews SET user_id = 7 WHERE id = 11 AND user_id = 1;

-- ----------------------------------------------------------
-- 3. The verified flag
-- ----------------------------------------------------------
-- A review is verified when the purchase is confirmed. Where there
-- is no qualifying order — nobody bought the Hario V60, for one —
-- the honest state is "not verified", and the dashboard shows no
-- check mark. The check mark is a promise; this keeps it true.
UPDATE reviews r
SET is_verified = FALSE
WHERE r.is_verified = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM orders o
      JOIN order_items oi ON oi.order_id = o.id
      JOIN product_variants pv ON pv.id = oi.product_variant_id
      WHERE o.user_id = r.user_id
        AND pv.product_id = r.product_id
        AND o.status NOT IN ('CANCELLED', 'PENDING')
  );

-- ----------------------------------------------------------
-- 4. Brazil Mogiana's image
-- ----------------------------------------------------------
-- The primary image was a CONCERT photograph, with an alt text
-- claiming it was a cup of coffee.
DELETE FROM product_media
WHERE url LIKE '%photo-1516450360452-9312f5e86fc7%';

-- The cup of black coffee becomes the primary image: it is the same
-- visual family as the other three coffees (ethiopia, colombia,
-- kenya all lead with a cup), and a full-bodied Typica suits a
-- black coffee better than a beans texture does.
UPDATE product_media
SET is_primary = FALSE,
    sort_order = 2
WHERE url LIKE '%photo-1521302080334-4bebac2763a6%'
  AND EXISTS (SELECT 1 FROM products p WHERE p.id = product_media.product_id
              AND p.slug = 'brazil-mogiana');

INSERT INTO product_media (product_id, url, alt_text, type, sort_order, is_primary)
SELECT p.id,
       'https://images.unsplash.com/photo-1521302080334-4bebac2763a6?w=800',
       'Brazil Mogiana washed Typica, brewed as a full-bodied black coffee',
       'IMAGE',
       0,
       TRUE
FROM products p
WHERE p.slug = 'brazil-mogiana'
  AND NOT EXISTS (
      SELECT 1 FROM product_media m
      WHERE m.product_id = p.id
        AND m.url = 'https://images.unsplash.com/photo-1521302080334-4bebac2763a6?w=800'
  );

-- Colombia Huila still uses the same photograph as a secondary image,
-- which is fine — only the PRIMARY is what a card leads with.
UPDATE product_media
SET is_primary = FALSE
WHERE url LIKE '%photo-1521302080334-4bebac2763a6%'
  AND EXISTS (SELECT 1 FROM products p WHERE p.id = product_media.product_id
              AND p.slug = 'colombia-huila');

-- End of V111
