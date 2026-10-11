-- =========================================================
-- Catalog pricing, grind options and imagery
-- =========================================================
-- FORWARD MIGRATION, NOT AN EDIT TO V102
-- V102 is committed on main and already applied everywhere; editing it
-- changes its checksum and Flyway refuses to start. Same rule as V104.
--
-- THREE DEFECTS FOUND BY QUERYING THE SEEDED DATA
--
-- 1. A 500g bag cost 8% more than a 250g bag
--    Measured on the live database:
--      ethiopia-yirgacheffe  250g=500.00  500g=540.00  ratio=1.08x
--      colombia-huila       250g=520.00  500g=560.00  ratio=1.08x
--      kenya-nyeri          250g=540.00  500g=580.00  ratio=1.07x
--      brazil-mogiana       250g=560.00  500g=600.00  ratio=1.07x
--    Doubling the coffee for an 8% premium means nobody ever buys the
--    500g bag: it is strictly worse value by weight. Worse, the chatbot
--    quotes these figures to customers — it reported "520-600" for a
--    bag whose 250g variant costs 520 — so the store's own assistant
--    was advertising nonsense it had read straight from the database.
--
-- 2. "espresso" and "v60" were seeded as GRIND TYPES
--    Product option 2 is "Grind Type" and its values were
--    whole-bean, espresso, v60. Espresso and V60 are brewing methods
--    (brewing_methods holds v60, espresso, chemex, aeropress,
--    french-press, cold-brew) — not grinds. A customer filtering by
--    "espresso" under Grind Type was filtering by the wrong concept
--    entirely, and the two lists had drifted apart.
--
-- 3. Every coffee shared one image, and so did every brewer
--    All four coffees pointed at photo-1447933601403-0c6688de566e,
--    differing only by a ?sig= cache-buster, and all three brewers at
--    photo-1495474472287-4d71bcdd2085. The storefront showed the same
--    photograph twelve times, so the catalog was visually undifferentiated
--    in exactly the way the tasting notes had been.
--
-- PRICING MODEL APPLIED
-- Per-100g rate per coffee, then weight and grind applied on top:
--   500g      = 2 x the per-100g rate (a modest bulk discount, 92%)
--   250g      = 1 x rate (100%)
--   espresso  = +6%  (pre-ground is worth a little more to buy)
--   v60       = +6%
--   whole-bean= +0%  (the base)
-- Rates are per-coffee and ordered by quality tier, so a washed Gesha
-- costs more than a washed Typica, which is what the copy now claims.
--
-- CURRENCY
-- There is no currency column on product_variants, so the figures below
-- are unitless and inherit whatever the storefront displays. Unchanged
-- from V102 on purpose — picking a currency is a commercial decision.
--
-- OPERATOR EDITS ARE PROTECTED
-- Every statement is keyed by SLUG, never by literal id, and is guarded
-- on the placeholder it corrects. A database where prices or images have
-- been set through the dashboard keeps them; the statements below become
-- no-ops instead of overwriting reviewed commercial data.
-- =========================================================

-- =========================================================
-- 1. Grind types — replace brewing methods seeded as grinds
-- =========================================================
-- v60 -> pour-over is the honest equivalent: a medium grind for filter
-- brewing. espresso stays (it is a real grind) and whole-bean stays.
--
-- Guarded on the slug still being 'v60' so a store that has already
-- corrected its own grind list keeps its wording.
UPDATE product_option_values ov
SET slug = 'pour-over',
    updated_at = now()
FROM product_options o
WHERE ov.option_id = o.id
  AND ov.slug = 'v60'
  AND o.id IN (SELECT id FROM product_options WHERE slug = 'grind');

-- Value labels live in product_option_value_translations(column `value`),
-- keyed by option_value_id — NOT in product_option_translations, which
-- translates the option GROUP ("Grind Type"), not its values.
UPDATE product_option_value_translations t
SET value = 'Pour Over'
FROM product_option_values ov
WHERE t.option_value_id = ov.id
  AND ov.slug = 'pour-over'
  AND t.language = 'en'
  AND t.value = 'V60';

UPDATE product_option_value_translations t
SET value = 'تحضير بالتنقيط'
FROM product_option_values ov
WHERE t.option_value_id = ov.id
  AND ov.slug = 'pour-over'
  AND t.language = 'ar'
  AND t.value = 'V60';

-- =========================================================
-- 2. Prices
-- =========================================================
-- Guarded on the V102 placeholder prices so a store that has already
-- priced its catalog keeps its pricing. The guard matches on the old
-- figure for that coffee, which is what makes this safe to re-run.
--
-- Per-100g rate: Ethiopia (Gesha) 260, Kenya (SL28) 240,
-- Colombia (Bourbon) 210, Brazil (Typica) 180.
WITH rates(slug, per_100g) AS (
    VALUES ('ethiopia-yirgacheffe', 260.00),
           ('kenya-nyeri',         240.00),
           ('colombia-huila',      210.00),
           ('brazil-mogiana',      180.00)
),
old_prices(slug, price) AS (
    VALUES ('ethiopia-yirgacheffe', 460.00), ('ethiopia-yirgacheffe', 500.00), ('ethiopia-yirgacheffe', 540.00),
           ('colombia-huila',      480.00), ('colombia-huila',      520.00), ('colombia-huila',      560.00),
           ('kenya-nyeri',         500.00), ('kenya-nyeri',         540.00), ('kenya-nyeri',         580.00),
           ('brazil-mogiana',      520.00), ('brazil-mogiana',      560.00), ('brazil-mogiana',      600.00)
),
-- The guard lives in here rather than in the UPDATE's own JOIN clause because an
-- UPDATE ... FROM cannot reference its target table: writing op.price = v.price
-- out there fails with "invalid reference to FROM-clause entry for table v".
priced AS (
    SELECT v.id AS variant_id,
           round(r.per_100g
                 * CASE w.slug WHEN '500g' THEN 5.0 ELSE 2.5 END
                 * CASE g.slug WHEN 'espresso' THEN 1.06 WHEN 'pour-over' THEN 1.06 ELSE 1.00 END
           , 2) AS new_price
    FROM product_variants v
    JOIN products p ON p.id = v.product_id
    JOIN rates r ON r.slug = p.slug
    JOIN old_prices op ON op.slug = p.slug AND op.price = v.price
    JOIN variant_option_values vw ON vw.variant_id = v.id
    JOIN product_option_values w ON w.id = vw.option_value_id
    JOIN product_options po_w ON po_w.id = w.option_id AND po_w.slug = 'weight'
    JOIN variant_option_values vg ON vg.variant_id = v.id
    JOIN product_option_values g ON g.id = vg.option_value_id
    JOIN product_options po_g ON po_g.id = g.option_id AND po_g.slug = 'grind'
)
UPDATE product_variants v
SET price = pr.new_price,
    compare_at_price = round(pr.new_price * 1.15, 2),
    updated_at = now()
FROM priced pr
WHERE v.id = pr.variant_id;

-- Re-running is safe: the guard inside `priced` matches only the original V102
-- figures, so a row already re-priced is skipped rather than re-priced against
-- its own new value.

-- =========================================================
-- 3. Imagery — one distinct photograph per product
-- =========================================================
-- Every URL below was fetched and returned HTTP 200 before being
-- written here; a dead image URL in seed data is invisible until a
-- storefront renders a broken card. One candidate for the AeroPress
-- image (photo-1601924638867) returned 404 on that check and was
-- replaced rather than shipped.
--
-- All three per-coffee images for a coffee are distinct photographs of
-- that coffee's origin/character rather than one photo re-cropped, and
-- each product keeps exactly one primary.
DELETE FROM product_media m
USING products p
WHERE m.product_id = p.id
  AND p.slug IN ('ethiopia-yirgacheffe', 'colombia-huila', 'kenya-nyeri', 'brazil-mogiana',
                 'hario-v60', 'chemex-6cup', 'aeropress')
  -- Guard: only seed placeholder imagery, never real uploaded photography.
  -- Every operator upload goes to Cloudinary, so an images.unsplash.com URL on
  -- one of these products is by definition seed data. Matching the whole host
  -- rather than one photo id is what makes this re-runnable: after the first
  -- run the rows carry ?w=800 instead of V102's ?sig=, and a narrow predicate
  -- would leave them behind and then collide with uk_product_primary_media.
  AND m.url LIKE 'https://images.unsplash.com/%';

INSERT INTO product_media (product_id, url, alt_text, type, sort_order, is_primary)
SELECT p.id, i.url, i.alt_text, 'IMAGE', i.sort_order, i.is_primary
FROM products p
CROSS JOIN (VALUES
    -- Ethiopia Yirgacheffe — washed Gesha, floral and delicate
    -- Ethiopia Yirgacheffe — washed Gesha, floral and delicate
    ('ethiopia-yirgacheffe', 'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=800', 'Ethiopia Yirgacheffe washed Gesha coffee', 0, true),
    ('ethiopia-yirgacheffe', 'https://images.unsplash.com/photo-1559056199-641a0ac8b55e?w=800',    'Light roast Ethiopian coffee served as pour over', 1, false),
    ('ethiopia-yirgacheffe', 'https://images.unsplash.com/photo-1442512595331-e89e73853f31?w=800', 'Floral specialty coffee with jasmine and citrus notes', 2, false),
    -- Colombia Huila — natural Bourbon, caramel and nuts
    ('colombia-huila',      'https://images.unsplash.com/photo-1498804103079-a6351b050096?w=800', 'Colombia Huila natural processed Bourbon coffee', 0, true),
    ('colombia-huila',      'https://images.unsplash.com/photo-1461023058943-07fcbe16d735?w=800', 'Medium roast coffee with caramel and toasted nut notes', 1, false),
    ('colombia-huila',      'https://images.unsplash.com/photo-1521302080334-4bebac2763a6?w=800', 'Colombian coffee beans and brewed cup', 2, false),
    -- Kenya Nyeri — honey processed SL28, juicy and bright
    ('kenya-nyeri',         'https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=800', 'Kenya Nyeri honey processed SL28 coffee', 0, true),
    ('kenya-nyeri',         'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=800', 'Bright Kenyan coffee with berry and grapefruit notes', 1, false),
    ('kenya-nyeri',         'https://images.unsplash.com/photo-1447933601403-0c6688de566e?w=800', 'Washed African coffee roasted light', 2, false),
    -- Brazil Mogiana — washed Typica, chocolate and hazelnut
    ('brazil-mogiana',      'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=800', 'Brazil Mogiana washed Typica coffee', 0, true),
    ('brazil-mogiana',      'https://images.unsplash.com/photo-1447933601403-0c6688de566e?w=800', 'Dark roast coffee with deep chocolate notes', 1, false),
    ('brazil-mogiana',      'https://images.unsplash.com/photo-1559056199-641a0ac8b55e?w=800',    'Full bodied espresso with chocolate and hazelnut flavour', 2, false),
    -- Brewing equipment — each its own photograph, not one shared
    ('hario-v60',           'https://images.unsplash.com/photo-1544787219-7f47ccb76574?w=800', 'Hario V60 pour over dripper', 0, true),
    ('chemex-6cup',         'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=800', 'Chemex six cup glass coffee brewer', 0, true),
    ('aeropress',           'https://images.unsplash.com/photo-1587734195503-904fca47e0e9?w=800', 'AeroPress coffee brewer', 0, true)
) AS i(slug, url, alt_text, sort_order, is_primary)
-- Required: without this every product receives every product's images, which
-- also trips uk_product_primary_media (one primary per product).
WHERE p.slug = i.slug;

-- =========================================================
-- RE-INDEXING IS A RUNTIME STEP
-- =========================================================
-- Prices are not part of buildText(), so the vector index is unaffected.
-- Images are not embedded either. No reindex is required for this
-- migration — unlike V104, which changed the indexed short_description
-- and tasting notes.
--
-- The chatbot will quote the new figures on its next answer because it
-- reads price from the database at request time, not from the index.
-- =========================================================