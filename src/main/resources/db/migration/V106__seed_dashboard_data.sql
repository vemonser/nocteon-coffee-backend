-- ============================================================
-- V106: Seed additional dashboard and operational data
-- ============================================================

-- ----------------------------------------------------------
-- 1. Seed default promo codes (if not already present)
-- ----------------------------------------------------------

INSERT INTO promo_codes (code, discount_type, discount_value, min_order_amount, scope_type, max_total_redemptions, max_redemptions_per_user, valid_from, valid_until, is_active, created_at, updated_at)
SELECT * FROM (SELECT
    'WELCOME10'::VARCHAR AS code,
    'PERCENTAGE'::VARCHAR AS discount_type,
    10.00::NUMERIC AS discount_value,
    50.00::NUMERIC AS min_order_amount,
    'GLOBAL'::VARCHAR AS scope_type,
    100 AS max_total_redemptions,
    1 AS max_redemptions_per_user,
    NOW() AS valid_from,
    NOW() + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    NOW() AS created_at,
    NOW() AS updated_at) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM promo_codes WHERE code = 'WELCOME10')
LIMIT 1;

INSERT INTO promo_codes (code, discount_type, discount_value, min_order_amount, scope_type, max_total_redemptions, max_redemptions_per_user, valid_from, valid_until, is_active, created_at, updated_at)
SELECT * FROM (SELECT
    'SAVE20'::VARCHAR AS code,
    'PERCENTAGE'::VARCHAR AS discount_type,
    20.00::NUMERIC AS discount_value,
    100.00::NUMERIC AS min_order_amount,
    'GLOBAL'::VARCHAR AS scope_type,
    50 AS max_total_redemptions,
    1 AS max_redemptions_per_user,
    NOW() AS valid_from,
    NOW() + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    NOW() AS created_at,
    NOW() AS updated_at) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM promo_codes WHERE code = 'SAVE20')
LIMIT 1;

INSERT INTO promo_codes (code, discount_type, discount_value, min_order_amount, scope_type, max_total_redemptions, max_redemptions_per_user, valid_from, valid_until, is_active, created_at, updated_at)
SELECT * FROM (SELECT
    'COFFEE15'::VARCHAR AS code,
    'PERCENTAGE'::VARCHAR AS discount_type,
    15.00::NUMERIC AS discount_value,
    30.00::NUMERIC AS min_order_amount,
    'GLOBAL'::VARCHAR AS scope_type,
    75 AS max_total_redemptions,
    1 AS max_redemptions_per_user,
    NOW() AS valid_from,
    NOW() + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    NOW() AS created_at,
    NOW() AS updated_at) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM promo_codes WHERE code = 'COFFEE15')
LIMIT 1;

INSERT INTO promo_codes (code, discount_type, discount_value, min_order_amount, scope_type, max_total_redemptions, max_redemptions_per_user, valid_from, valid_until, is_active, created_at, updated_at)
SELECT * FROM (SELECT
    'FREESHIP'::VARCHAR AS code,
    'FREE_SHIPPING'::VARCHAR AS discount_type,
    0.00::NUMERIC AS discount_value,
    150.00::NUMERIC AS min_order_amount,
    'GLOBAL'::VARCHAR AS scope_type,
    50 AS max_total_redemptions,
    1 AS max_redemptions_per_user,
    NOW() AS valid_from,
    NOW() + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    NOW() AS created_at,
    NOW() AS updated_at) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM promo_codes WHERE code = 'FREESHIP')
LIMIT 1;

INSERT INTO promo_codes (code, discount_type, discount_value, min_order_amount, scope_type, max_total_redemptions, max_redemptions_per_user, valid_from, valid_until, is_active, created_at, updated_at)
SELECT * FROM (SELECT
    'NEWUSER25'::VARCHAR AS code,
    'PERCENTAGE'::VARCHAR AS discount_type,
    25.00::NUMERIC AS discount_value,
    0.00::NUMERIC AS min_order_amount,
    'GLOBAL'::VARCHAR AS scope_type,
    30 AS max_total_redemptions,
    1 AS max_redemptions_per_user,
    NOW() AS valid_from,
    NOW() + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    NOW() AS created_at,
    NOW() AS updated_at) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM promo_codes WHERE code = 'NEWUSER25')
LIMIT 1;

INSERT INTO promo_codes (code, discount_type, discount_value, min_order_amount, scope_type, max_total_redemptions, max_redemptions_per_user, valid_from, valid_until, is_active, created_at, updated_at)
SELECT * FROM (SELECT
    'LOYALTY5'::VARCHAR AS code,
    'PERCENTAGE'::VARCHAR AS discount_type,
    5.00::NUMERIC AS discount_value,
    200.00::NUMERIC AS min_order_amount,
    'GLOBAL'::VARCHAR AS scope_type,
    200 AS max_total_redemptions,
    1 AS max_redemptions_per_user,
    NOW() AS valid_from,
    NOW() + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    NOW() AS created_at,
    NOW() AS updated_at) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM promo_codes WHERE code = 'LOYALTY5')
LIMIT 1;

INSERT INTO promo_codes (code, discount_type, discount_value, min_order_amount, scope_type, max_total_redemptions, max_redemptions_per_user, valid_from, valid_until, is_active, created_at, updated_at)
SELECT * FROM (SELECT
    'BULK10'::VARCHAR AS code,
    'PERCENTAGE'::VARCHAR AS discount_type,
    10.00::NUMERIC AS discount_value,
    300.00::NUMERIC AS min_order_amount,
    'GLOBAL'::VARCHAR AS scope_type,
    25 AS max_total_redemptions,
    1 AS max_redemptions_per_user,
    NOW() AS valid_from,
    NOW() + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    NOW() AS created_at,
    NOW() AS updated_at) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM promo_codes WHERE code = 'BULK10')
LIMIT 1;

INSERT INTO promo_codes (code, discount_type, discount_value, min_order_amount, scope_type, max_total_redemptions, max_redemptions_per_user, valid_from, valid_until, is_active, created_at, updated_at)
SELECT * FROM (SELECT
    'SEASONAL20'::VARCHAR AS code,
    'PERCENTAGE'::VARCHAR AS discount_type,
    20.00::NUMERIC AS discount_value,
    100.00::NUMERIC AS min_order_amount,
    'GLOBAL'::VARCHAR AS scope_type,
    40 AS max_total_redemptions,
    1 AS max_redemptions_per_user,
    NOW() AS valid_from,
    NOW() + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    NOW() AS created_at,
    NOW() AS updated_at) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM promo_codes WHERE code = 'SEASONAL20')
LIMIT 1;

-- ----------------------------------------------------------
-- 2. Seed default shipping zones with is_active (not active)
-- ----------------------------------------------------------

-- Cairo Zone
INSERT INTO shipping_zones (name, shipping_cost, is_active, created_at, updated_at)
SELECT 'Cairo Zone'::VARCHAR, 50.00::NUMERIC, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM shipping_zones WHERE name = 'Cairo Zone')
LIMIT 1;

-- Alexandria Zone
INSERT INTO shipping_zones (name, shipping_cost, is_active, created_at, updated_at)
SELECT 'Alexandria Zone'::VARCHAR, 75.00::NUMERIC, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM shipping_zones WHERE name = 'Alexandria Zone')
LIMIT 1;

-- Giza Zone
INSERT INTO shipping_zones (name, shipping_cost, is_active, created_at, updated_at)
SELECT 'Giza Zone'::VARCHAR, 60.00::NUMERIC, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM shipping_zones WHERE name = 'Giza Zone')
LIMIT 1;

-- Delta Zone
INSERT INTO shipping_zones (name, shipping_cost, is_active, created_at, updated_at)
SELECT 'Delta Zone'::VARCHAR, 80.00::NUMERIC, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM shipping_zones WHERE name = 'Delta Zone')
LIMIT 1;

-- Upper Egypt Zone
INSERT INTO shipping_zones (name, shipping_cost, is_active, created_at, updated_at)
SELECT 'Upper Egypt Zone'::VARCHAR, 100.00::NUMERIC, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM shipping_zones WHERE name = 'Upper Egypt Zone')
LIMIT 1;

-- ----------------------------------------------------------
-- 3. Seed cities for each shipping zone using subqueries
-- ----------------------------------------------------------

-- Cairo Zone cities (5 cities) - get shipping_zone_id by name
INSERT INTO shipping_zone_cities (shipping_zone_id, city)
SELECT id, 'Cairo' FROM shipping_zones WHERE name = 'Cairo Zone'
UNION ALL SELECT id, 'New Cairo' FROM shipping_zones WHERE name = 'Cairo Zone'
UNION ALL SELECT id, 'Nasr City' FROM shipping_zones WHERE name = 'Cairo Zone'
UNION ALL SELECT id, 'Heliopolis' FROM shipping_zones WHERE name = 'Cairo Zone'
UNION ALL SELECT id, 'Maadi' FROM shipping_zones WHERE name = 'Cairo Zone';

-- Alexandria Zone cities (4 cities)
INSERT INTO shipping_zone_cities (shipping_zone_id, city)
SELECT id, 'Alexandria' FROM shipping_zones WHERE name = 'Alexandria Zone'
UNION ALL SELECT id, 'Smouha' FROM shipping_zones WHERE name = 'Alexandria Zone'
UNION ALL SELECT id, 'Montazah' FROM shipping_zones WHERE name = 'Alexandria Zone'
UNION ALL SELECT id, 'Agamy' FROM shipping_zones WHERE name = 'Alexandria Zone';

-- Giza Zone cities (4 cities)
INSERT INTO shipping_zone_cities (shipping_zone_id, city)
SELECT id, 'Giza' FROM shipping_zones WHERE name = 'Giza Zone'
UNION ALL SELECT id, '6th October' FROM shipping_zones WHERE name = 'Giza Zone'
UNION ALL SELECT id, 'Sheikh Zayed' FROM shipping_zones WHERE name = 'Giza Zone'
UNION ALL SELECT id, 'Dokki' FROM shipping_zones WHERE name = 'Giza Zone';

-- Delta Zone cities (4 cities)
INSERT INTO shipping_zone_cities (shipping_zone_id, city)
SELECT id, 'Tanta' FROM shipping_zones WHERE name = 'Delta Zone'
UNION ALL SELECT id, 'Mansoura' FROM shipping_zones WHERE name = 'Delta Zone'
UNION ALL SELECT id, 'Zagazig' FROM shipping_zones WHERE name = 'Delta Zone'
UNION ALL SELECT id, 'Damanhour' FROM shipping_zones WHERE name = 'Delta Zone';

-- Upper Egypt Zone cities (4 cities)
INSERT INTO shipping_zone_cities (shipping_zone_id, city)
SELECT id, 'Assiut' FROM shipping_zones WHERE name = 'Upper Egypt Zone'
UNION ALL SELECT id, 'Sohag' FROM shipping_zones WHERE name = 'Upper Egypt Zone'
UNION ALL SELECT id, 'Luxor' FROM shipping_zones WHERE name = 'Upper Egypt Zone'
UNION ALL SELECT id, 'Aswan' FROM shipping_zones WHERE name = 'Upper Egypt Zone';

-- ----------------------------------------------------------
-- 4. Seed default store settings with EGP currency
-- ----------------------------------------------------------

INSERT INTO store_settings (id, free_shipping_threshold, currency, created_at, updated_at)
SELECT 1, 200.00, 'EGP'::VARCHAR, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM store_settings WHERE id = 1)
LIMIT 1;

-- ----------------------------------------------------------
-- 5. Seed a few default products with translations
-- ----------------------------------------------------------

-- Ethiopia Yirgacheffe
INSERT INTO products (slug, category_id, product_type, featured, is_active, created_at, updated_at)
SELECT 'ethiopia-yirgacheffe', c.id, 'COFFEE', FALSE, TRUE, NOW(), NOW()
FROM categories c WHERE c.slug = 'coffee-beans'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'en', 'Ethiopia Yirgacheffe', 'Ethiopian Yirgacheffe coffee beans', 'Premium Ethiopia Yirgacheffe beans from the Yirgacheffe region of Ethiopia', NOW(), NOW()
FROM products p WHERE p.slug = 'ethiopia-yirgacheffe'
ON CONFLICT (product_id, language) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'ar', 'إثيوبيا ييرغاشيف', 'حبوب قهوة إثيوبية ييرغاشيف', 'حبوب قهوة إثيوبية عالية الجودة من منطقة ييرغاشيف', NOW(), NOW()
FROM products p WHERE p.slug = 'ethiopia-yirgacheffe'
ON CONFLICT (product_id, language) DO NOTHING;

-- Colombia Huila
INSERT INTO products (slug, category_id, product_type, featured, is_active, created_at, updated_at)
SELECT 'colombia-huila', c.id, 'COFFEE', FALSE, TRUE, NOW(), NOW()
FROM categories c WHERE c.slug = 'coffee-beans'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'en', 'Colombia Huila', 'Colombia Huila coffee beans', 'Premium Colombia Huila beans from the Huila region', NOW(), NOW()
FROM products p WHERE p.slug = 'colombia-huila'
ON CONFLICT (product_id, language) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'ar', 'كولومبيا هيل라', 'حبوب القهوة الكولومبية هيل라', 'حبوب قهوة كولومبية عالية الجودة من منطقة هيل라', NOW(), NOW()
FROM products p WHERE p.slug = 'colombia-huila'
ON CONFLICT (product_id, language) DO NOTHING;

-- Kenya Nyeri
INSERT INTO products (slug, category_id, product_type, featured, is_active, created_at, updated_at)
SELECT 'kenya-nyeri', c.id, 'COFFEE', FALSE, TRUE, NOW(), NOW()
FROM categories c WHERE c.slug = 'coffee-beans'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'en', 'Kenya Nyeri', 'Kenya Nyeri coffee beans', 'Premium Kenya Nyeri beans from the Nyeri region', NOW(), NOW()
FROM products p WHERE p.slug = 'kenya-nyeri'
ON CONFLICT (product_id, language) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'ar', 'كينيا نايري', 'حبوب القهوة الكينية نايري', 'حبوب قهوة كينية عالية الجودة من منطقة نايري', NOW(), NOW()
FROM products p WHERE p.slug = 'kenya-nyeri'
ON CONFLICT (product_id, language) DO NOTHING;

-- Guatemala Antigua
INSERT INTO products (slug, category_id, product_type, featured, is_active, created_at, updated_at)
SELECT 'guatemala-antigua', c.id, 'COFFEE', FALSE, TRUE, NOW(), NOW()
FROM categories c WHERE c.slug = 'coffee-beans'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'en', 'Guatemala Antigua', 'Guatemala Antigua coffee beans', 'Premium Guatemala Antigua beans from the Antigua region', NOW(), NOW()
FROM products p WHERE p.slug = 'guatemala-antigua'
ON CONFLICT (product_id, language) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'ar', 'غواتيمالا أنطiqua', 'حبوب القهوة الغواتيمالية أنطigua', 'حبوب قهوة غواتيمالية عالية الجودة من منطقه أنطigua', NOW(), NOW()
FROM products p WHERE p.slug = 'guatemala-antiqua'
ON CONFLICT (product_id, language) DO NOTHING;

-- Costa Rica Tarrazu (featured)
INSERT INTO products (slug, category_id, product_type, featured, is_active, created_at, updated_at)
SELECT 'costa-rica-tarrazu', c.id, 'COFFEE', TRUE, TRUE, NOW(), NOW()
FROM categories c WHERE c.slug = 'coffee-beans'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'en', 'Costa Rica Tarrazu', 'Costa Rica Tarrazu coffee beans', 'Premium Costa Rica Tarrazu beans from the Tarrazu region', NOW(), NOW()
FROM products p WHERE p.slug = 'costa-rica-tarrazu'
ON CONFLICT (product_id, language) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'ar', 'كوستاريكا تارابو', 'حبوب القهوة الكوستاريكية تارابو', 'حبوب قهوة كوستاريكية عالية الجودة من منطقه تارابو', NOW(), NOW()
FROM products p WHERE p.slug = 'costa-rica-tarrazu'
ON CONFLICT (product_id, language) DO NOTHING;

-- Brazil Santos
INSERT INTO products (slug, category_id, product_type, featured, is_active, created_at, updated_at)
SELECT 'brazil-santos', c.id, 'COFFEE', FALSE, TRUE, NOW(), NOW()
FROM categories c WHERE c.slug = 'coffee-beans'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'en', 'Brazil Santos', 'Brazil Santos coffee beans', 'Premium Brazil Santos beans', NOW(), NOW()
FROM products p WHERE p.slug = 'brazil-santos'
ON CONFLICT (product_id, language) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'ar', 'برازيل سانتوس', 'حبوب القهوة البرازيلية سانتوس', 'حبوب قهوة برازيلية عالية الجودة', NOW(), NOW()
FROM products p WHERE p.slug = 'brazil-santos'
ON CONFLICT (product_id, language) DO NOTHING;

-- ----------------------------------------------------------
-- End of V106 seed data migration
-- ============================================================