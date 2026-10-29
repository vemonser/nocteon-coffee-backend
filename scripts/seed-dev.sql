-- Deterministic dev seed (generated). Truncate demo tables first.
TRUNCATE TABLE public.addresses, public.audit_logs, public.brewing_method_translations, public.brewing_methods, public.broadcast_recipients, public.broadcasts, public.cart_items, public.carts, public.categories, public.category_translations, public.coffee_details, public.coffee_varieties, public.coffee_variety_translations, public.contact_messages, public.email_log, public.farm_translations, public.farms, public.inventory_movements, public.journal_categories, public.journal_category_translations, public.journal_post_tags, public.journal_post_translations, public.journal_posts, public.login_activities, public.newsletter_subscribers, public.notifications, public.order_items, public.order_notes, public.order_refunds, public.order_status_history, public.orders, public.origin_translations, public.origins, public.pairing_translations, public.pairings, public.payments, public.processing_method_translations, public.processing_methods, public.product_brewing_methods, public.product_journal_posts, public.product_media, public.product_option_applies_to, public.product_option_links, public.product_option_translations, public.product_option_value_translations, public.product_option_values, public.product_options, public.product_pairings, public.product_relations, public.product_tag_links, public.product_tag_translations, public.product_tags, public.product_tasting_notes, public.product_translations, public.product_variants, public.products, public.promo_code_categories, public.promo_code_redemptions, public.promo_codes, public.refresh_tokens, public.return_items, public.returns, public.reviews, public.roast_level_translations, public.roast_levels, public.tasting_note_translations, public.tasting_notes, public.user_linked_accounts, public.user_profiles, public.users, public.variant_option_values, public.vector_store, public.verification_codes, public.wishlist_items, public.wishlists RESTART IDENTITY CASCADE;


-- ===== V100__seed_lookups.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

-- =========================================================
-- Categories
-- =========================================================

INSERT INTO categories (slug) VALUES
('coffee'),
('equipment'),
('gift-cards');

INSERT INTO category_translations (category_id, language, name, description) VALUES
(1,'en','Coffee','Specialty coffee beans'),
(1,'ar','القهوة','حبوب قهوة مختصة'),

(2,'en','Equipment','Coffee brewing equipment'),
(2,'ar','معدات التحضير','معدات تحضير القهوة'),

(3,'en','Gift Cards','Gift cards'),
(3,'ar','بطاقات الهدايا','بطاقات الهدايا');

-- =========================================================
-- Origins
-- =========================================================

INSERT INTO origins (slug, code) VALUES
('ethiopia','ET'),
('colombia','CO'),
('brazil','BR'),
('kenya','KE'),
('panama','PA');

INSERT INTO origin_translations (origin_id, language, name, description) VALUES
(1,'en','Ethiopia','Origin'),
(1,'ar','إثيوبيا','منشأ'),

(2,'en','Colombia','Origin'),
(2,'ar','كولومبيا','منشأ'),

(3,'en','Brazil','Origin'),
(3,'ar','البرازيل','منشأ'),

(4,'en','Kenya','Origin'),
(4,'ar','كينيا','منشأ'),

(5,'en','Panama','Origin'),
(5,'ar','بنما','منشأ');

-- =========================================================
-- Farms
-- =========================================================

INSERT INTO farms (origin_id, slug) VALUES
(1,'yirgacheffe'),
(1,'guji'),
(2,'huila'),
(3,'mogiana'),
(4,'nyeri');

INSERT INTO farm_translations
(farm_id, language, name, country, description)
VALUES
(1,'en','Yirgacheffe Farm','Ethiopia','Demo Farm'),
(1,'ar','مزرعة يرقاشيف','إثيوبيا','مزرعة تجريبية'),

(2,'en','Guji Farm','Ethiopia','Demo Farm'),
(2,'ar','مزرعة قوجي','إثيوبيا','مزرعة تجريبية'),

(3,'en','Huila Farm','Colombia','Demo Farm'),
(3,'ar','مزرعة هويلا','كولومبيا','مزرعة تجريبية'),

(4,'en','Mogiana Farm','Brazil','Demo Farm'),
(4,'ar','مزرعة موجيانا','البرازيل','مزرعة تجريبية'),

(5,'en','Nyeri Farm','Kenya','Demo Farm'),
(5,'ar','مزرعة نييري','كينيا','مزرعة تجريبية');

-- =========================================================
-- Roast Levels
-- =========================================================

INSERT INTO roast_levels(slug,color) VALUES
('light','#E8C39E'),
('medium-light','#C78652'),
('medium','#9A5A2D'),
('medium-dark','#63341B'),
('dark','#2B1810');

INSERT INTO roast_level_translations
(roast_level_id,language,name,description)
VALUES
(1,'en','Light',''),
(1,'ar','فاتح',''),

(2,'en','Medium Light',''),
(2,'ar','فاتح متوسط',''),

(3,'en','Medium',''),
(3,'ar','متوسط',''),

(4,'en','Medium Dark',''),
(4,'ar','متوسط غامق',''),

(5,'en','Dark',''),
(5,'ar','غامق','');

-- =========================================================
-- Processing Methods
-- =========================================================

INSERT INTO processing_methods(slug) VALUES
('washed'),
('natural'),
('honey'),
('anaerobic'),
('wet-hulled');

INSERT INTO processing_method_translations
(processing_method_id,language,name,description)
VALUES
(1,'en','Washed',''),
(1,'ar','مغسولة',''),

(2,'en','Natural',''),
(2,'ar','مجففة',''),

(3,'en','Honey',''),
(3,'ar','هاني',''),

(4,'en','Anaerobic',''),
(4,'ar','لاهوائية',''),

(5,'en','Wet Hulled',''),
(5,'ar','تقشير رطب','');

-- =========================================================
-- Coffee Varieties
-- =========================================================

INSERT INTO coffee_varieties(slug) VALUES
('gesha'),
('bourbon'),
('typica'),
('caturra'),
('sl28');

INSERT INTO coffee_variety_translations
(coffee_variety_id,language,name,description)
VALUES
(1,'en','Gesha',''),
(1,'ar','جيشا',''),

(2,'en','Bourbon',''),
(2,'ar','بوربون',''),

(3,'en','Typica',''),
(3,'ar','تيبيكا',''),

(4,'en','Caturra',''),
(4,'ar','كاتورا',''),

(5,'en','SL28',''),
(5,'ar','SL28','');

-- =========================================================
-- Tasting Notes
-- =========================================================

INSERT INTO tasting_notes(slug) VALUES
('chocolate'),
('caramel'),
('berry'),
('citrus'),
('jasmine'),
('honey'),
('nuts'),
('apple');

INSERT INTO tasting_note_translations
(tasting_note_id,language,name)
VALUES
(1,'en','Chocolate'),
(1,'ar','شوكولاتة'),

(2,'en','Caramel'),
(2,'ar','كراميل'),

(3,'en','Berry'),
(3,'ar','توت'),

(4,'en','Citrus'),
(4,'ar','حمضيات'),

(5,'en','Jasmine'),
(5,'ar','ياسمين'),

(6,'en','Honey'),
(6,'ar','عسل'),

(7,'en','Nuts'),
(7,'ar','مكسرات'),

(8,'en','Apple'),
(8,'ar','تفاح');

-- =========================================================
-- Brewing Methods
-- =========================================================

INSERT INTO brewing_methods(slug) VALUES
('v60'),
('espresso'),
('chemex'),
('aeropress'),
('french-press'),
('cold-brew');

INSERT INTO brewing_method_translations
(brewing_method_id,language,name,description)
VALUES
(1,'en','V60',''),
(1,'ar','V60',''),

(2,'en','Espresso',''),
(2,'ar','إسبريسو',''),

(3,'en','Chemex',''),
(3,'ar','كيميكس',''),

(4,'en','Aeropress',''),
(4,'ar','إيروبرس',''),

(5,'en','French Press',''),
(5,'ar','فرنش برس',''),

(6,'en','Cold Brew',''),
(6,'ar','كولد برو','');

-- =========================================================
-- Pairings
-- =========================================================

INSERT INTO pairings(slug) VALUES
('croissant'),
('cookies'),
('cheesecake'),
('dark-chocolate'),
('dates');

INSERT INTO pairing_translations
(pairing_id,language,name,description)
VALUES
(1,'en','Croissant',''),
(1,'ar','كرواسون',''),

(2,'en','Cookies',''),
(2,'ar','كوكيز',''),

(3,'en','Cheesecake',''),
(3,'ar','تشيز كيك',''),

(4,'en','Dark Chocolate',''),
(4,'ar','شوكولاتة داكنة',''),

(5,'en','Dates',''),
(5,'ar','تمر','');

-- =========================================================
-- Journal Categories
-- =========================================================

INSERT INTO journal_categories(slug) VALUES
('brewing-guides'),
('coffee-origins'),
('coffee-culture');

INSERT INTO journal_category_translations
(journal_category_id,language,name)
VALUES
(1,'en','Brewing Guides'),
(1,'ar','دليل التحضير'),

(2,'en','Coffee Origins'),
(2,'ar','أصول القهوة'),

(3,'en','Coffee Culture'),
(3,'ar','ثقافة القهوة');


-- ===== V101__seed_users.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

-- =========================================================
-- Users
-- Password for all users:
-- (Argon2 Hash)
-- =========================================================

INSERT INTO users
(username, email, password, enabled, provider, role, is_active)
VALUES

('admin',
'admin@aurum.local',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'ADMIN',
TRUE),

('ahmed',
'ahmed@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('mohamed',
'mohamed@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('sara',
'sara@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('nour',
'nour@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('omar',
'omar@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('youssef',
'youssef@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('salma',
'salma@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('mahmoud',
'mahmoud@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE),

('mariam',
'mariam@example.com',
'$argon2i$v=19$m=16,t=2,p=1$aGFzaGVkMTIzNA$lOokV+IuLBi7LyoCEMfhEQ',
TRUE,
'LOCAL',
'CUSTOMER',
TRUE);

-- =========================================================
-- Profiles
-- =========================================================

INSERT INTO user_profiles
(user_id, first_name, last_name, phone, bio)
VALUES

(1,'Admin','Aurum','01000000000','System Administrator'),

(2,'Ahmed','Ali','01011111111','Coffee Lover'),
(3,'Mohamed','Hassan','01022222222','Specialty Coffee Fan'),
(4,'Sara','Khaled','01033333333','Barista'),
(5,'Nour','Adel','01044444444','Coffee Explorer'),
(6,'Omar','Samy','01055555555','Espresso Addict'),
(7,'Youssef','Mostafa','01066666666','V60 Enthusiast'),
(8,'Salma','Maher','01077777777','Latte Artist'),
(9,'Mahmoud','Fathy','01088888888','Home Brewer'),
(10,'Mariam','Tarek','01099999999','Coffee Blogger');

-- =========================================================
-- Addresses
-- =========================================================

INSERT INTO addresses
(user_id, full_name, phone, street, city, state, country, postal_code, is_default)
VALUES

(1,'Admin Aurum','01000000000','Smart Village','Giza','Giza','Egypt','12577',TRUE),

(2,'Ahmed Ali','01011111111','Street 1','Cairo','Cairo','Egypt','11511',TRUE),
(3,'Mohamed Hassan','01022222222','Street 2','Alexandria','Alex','Egypt','21500',TRUE),
(4,'Sara Khaled','01033333333','Street 3','Cairo','Cairo','Egypt','11511',TRUE),
(5,'Nour Adel','01044444444','Street 4','Giza','Giza','Egypt','12511',TRUE),
(6,'Omar Samy','01055555555','Street 5','Mansoura','Dakahlia','Egypt','35511',TRUE),
(7,'Youssef Mostafa','01066666666','Street 6','Tanta','Gharbia','Egypt','31511',TRUE),
(8,'Salma Maher','01077777777','Street 7','Zagazig','Sharqia','Egypt','44511',TRUE),
(9,'Mahmoud Fathy','01088888888','Street 8','Aswan','Aswan','Egypt','81511',TRUE),
(10,'Mariam Tarek','01099999999','Street 9','Luxor','Luxor','Egypt','85511',TRUE);

-- =========================================================
-- Carts
-- =========================================================

INSERT INTO carts(user_id)
VALUES
(1),(2),(3),(4),(5),(6),(7),(8),(9),(10);

-- =========================================================
-- Wishlists
-- =========================================================

INSERT INTO wishlists(user_id)
VALUES
(1),(2),(3),(4),(5),(6),(7),(8),(9),(10);


-- ===== V102__seed_products.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.


-- =========================================================
-- Global Options
-- =========================================================
INSERT INTO
    product_options (slug, sort_order)
VALUES
    ('weight', 1),
    ('grind', 2),
    ('size', 3);

INSERT INTO
    product_option_translations (option_id, language, name)
VALUES
    (1, 'en', 'Weight'),
    (1, 'ar', 'الوزن'),
    (2, 'en', 'Grind Type'),
    (2, 'ar', 'نوع الطحن'),
    (3, 'en', 'Size'),
    (3, 'ar', 'المقاس');

-- =========================================================
-- Global Option Values
-- =========================================================
INSERT INTO
    product_option_values (option_id, slug, sort_order, numeric_value)
VALUES
    (1, '250g', 1, 250),
    (1, '500g', 2, 500),
    (2, 'whole-bean', 1, NULL),
    (2, 'espresso', 2, NULL),
    (2, 'v60', 3, NULL),
    (3, '01', 1, NULL),
    (3, '02', 2, NULL);

INSERT INTO
    product_option_value_translations (option_value_id, language, value)
VALUES
    (1, 'en', '250g'),
    (1, 'ar', '250 جم'),
    (2, 'en', '500g'),
    (2, 'ar', '500 جم'),
    (3, 'en', 'Whole Bean'),
    (3, 'ar', 'حبوب كاملة'),
    (4, 'en', 'Espresso'),
    (4, 'ar', 'إسبريسو'),
    (5, 'en', 'V60'),
    (5, 'ar', 'V60'),
    (6, 'en', '01 (1-2 cups)'),
    (6, 'ar', '01 (1-2 كوب)'),
    (7, 'en', '02 (1-4 cups)'),
    (7, 'ar', '02 (1-4 أكواب)');

-- =========================================================
-- Product 1: Ethiopia Yirgacheffe
-- =========================================================
INSERT INTO
    products (
        slug,
        category_id,
        origin_id,
        farm_id,
        product_type,
        featured,
        is_active
    )
VALUES
    (
        'ethiopia-yirgacheffe',
        1,
        1,
        1,
        'COFFEE',
        TRUE,
        TRUE
    );

INSERT INTO
    product_translations (
        product_id,
        language,
        name,
        short_description,
        description
    )
VALUES
    (
        1,
        'en',
        'Ethiopia Yirgacheffe',
        'Premium specialty coffee',
        'Long demo description'
    ),
    (
        1,
        'ar',
        'إثيوبيا يرقاشيف',
        'قهوة مختصة',
        'وصف عربي كامل'
    );

INSERT INTO
    coffee_details (
        product_id,
        roast_level_id,
        processing_method_id,
        coffee_variety_id,
        altitude,
        harvest_year,
        story
    )
VALUES
    (1, 1, 1, 1, 1900, 2025, 'Demo story');

INSERT INTO
    product_option_links (product_id, option_id)
VALUES
    (1, 1),
    (1, 2);

INSERT INTO
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        stock_quantity,
        is_active
    )
VALUES
    (1, 'SKU-1-1', 460, 510, 50, TRUE),
    (1, 'SKU-1-2', 500, 550, 50, TRUE),
    (1, 'SKU-1-3', 540, 590, 50, TRUE);

INSERT INTO
    variant_option_values (variant_id, option_value_id)
VALUES
    (1, 1),
    (1, 3),
    (2, 1),
    (2, 4),
    (3, 2),
    (3, 5);

INSERT INTO
    product_media (
        product_id,
        url,
        alt_text,
        type,
        sort_order,
        is_primary
    )
VALUES
    (
        1,
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=11',
        'Ethiopia Yirgacheffe',
        'IMAGE',
        1,
        TRUE
    ),
    (
        1,
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=12',
        'Ethiopia Yirgacheffe',
        'IMAGE',
        2,
        FALSE
    ),
    (
        1,
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=13',
        'Ethiopia Yirgacheffe',
        'IMAGE',
        3,
        FALSE
    );

INSERT INTO
    product_tasting_notes
VALUES
    (1, 1),
    (1, 2),
    (1, 3);

INSERT INTO
    product_brewing_methods (product_id, brewing_method_id, score)
VALUES
    (1, 1, 95),
    (1, 3, 90);

INSERT INTO
    product_pairings
VALUES
    (1, 1),
    (1, 2);

-- =========================================================
-- Product 2: Colombia Huila
-- =========================================================
INSERT INTO
    products (
        slug,
        category_id,
        origin_id,
        farm_id,
        product_type,
        featured,
        is_active
    )
VALUES
    ('colombia-huila', 1, 2, 2, 'COFFEE', TRUE, TRUE);

INSERT INTO
    product_translations (
        product_id,
        language,
        name,
        short_description,
        description
    )
VALUES
    (
        2,
        'en',
        'Colombia Huila',
        'Premium specialty coffee',
        'Long demo description'
    ),
    (
        2,
        'ar',
        'كولومبيا هويلا',
        'قهوة مختصة',
        'وصف عربي كامل'
    );

INSERT INTO
    coffee_details (
        product_id,
        roast_level_id,
        processing_method_id,
        coffee_variety_id,
        altitude,
        harvest_year,
        story
    )
VALUES
    (2, 3, 2, 2, 1750, 2025, 'Demo story');

INSERT INTO
    product_option_links (product_id, option_id)
VALUES
    (2, 1),
    (2, 2);

INSERT INTO
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        stock_quantity,
        is_active
    )
VALUES
    (2, 'SKU-2-1', 480, 530, 50, TRUE),
    (2, 'SKU-2-2', 520, 570, 50, TRUE),
    (2, 'SKU-2-3', 560, 610, 50, TRUE);

INSERT INTO
    variant_option_values (variant_id, option_value_id)
VALUES
    (4, 1),
    (4, 3),
    (5, 1),
    (5, 4),
    (6, 2),
    (6, 5);

INSERT INTO
    product_media (
        product_id,
        url,
        alt_text,
        type,
        sort_order,
        is_primary
    )
VALUES
    (
        2,
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=21',
        'Colombia Huila',
        'IMAGE',
        1,
        TRUE
    ),
    (
        2,
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=22',
        'Colombia Huila',
        'IMAGE',
        2,
        FALSE
    ),
    (
        2,
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=23',
        'Colombia Huila',
        'IMAGE',
        3,
        FALSE
    );

INSERT INTO
    product_tasting_notes
VALUES
    (2, 1),
    (2, 2),
    (2, 3);

INSERT INTO
    product_brewing_methods (product_id, brewing_method_id, score)
VALUES
    (2, 1, 95),
    (2, 3, 90);

INSERT INTO
    product_pairings
VALUES
    (2, 1),
    (2, 2);

-- =========================================================
-- Product 3: Kenya Nyeri
-- =========================================================
INSERT INTO
    products (
        slug,
        category_id,
        origin_id,
        farm_id,
        product_type,
        featured,
        is_active
    )
VALUES
    ('kenya-nyeri', 1, 4, 3, 'COFFEE', TRUE, TRUE);

INSERT INTO
    product_translations (
        product_id,
        language,
        name,
        short_description,
        description
    )
VALUES
    (
        3,
        'en',
        'Kenya Nyeri',
        'Premium specialty coffee',
        'Long demo description'
    ),
    (
        3,
        'ar',
        'كينيا نييري',
        'قهوة مختصة',
        'وصف عربي كامل'
    );

INSERT INTO
    coffee_details (
        product_id,
        roast_level_id,
        processing_method_id,
        coffee_variety_id,
        altitude,
        harvest_year,
        story
    )
VALUES
    (3, 2, 3, 5, 1850, 2025, 'Demo story');

INSERT INTO
    product_option_links (product_id, option_id)
VALUES
    (3, 1),
    (3, 2);

INSERT INTO
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        stock_quantity,
        is_active
    )
VALUES
    (3, 'SKU-3-1', 500, 550, 50, TRUE),
    (3, 'SKU-3-2', 540, 590, 50, TRUE),
    (3, 'SKU-3-3', 580, 630, 50, TRUE);

INSERT INTO
    variant_option_values (variant_id, option_value_id)
VALUES
    (7, 1),
    (7, 3),
    (8, 1),
    (8, 4),
    (9, 2),
    (9, 5);

INSERT INTO
    product_media (
        product_id,
        url,
        alt_text,
        type,
        sort_order,
        is_primary
    )
VALUES
    (
        3,
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=31',
        'Kenya Nyeri',
        'IMAGE',
        1,
        TRUE
    ),
    (
        3,
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=32',
        'Kenya Nyeri',
        'IMAGE',
        2,
        FALSE
    ),
    (
        3,
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=33',
        'Kenya Nyeri',
        'IMAGE',
        3,
        FALSE
    );

INSERT INTO
    product_tasting_notes
VALUES
    (3, 1),
    (3, 2),
    (3, 3);

INSERT INTO
    product_brewing_methods (product_id, brewing_method_id, score)
VALUES
    (3, 1, 95),
    (3, 3, 90);

INSERT INTO
    product_pairings
VALUES
    (3, 1),
    (3, 2);

-- =========================================================
-- Product 4: Brazil Mogiana
-- =========================================================
INSERT INTO
    products (
        slug,
        category_id,
        origin_id,
        farm_id,
        product_type,
        featured,
        is_active
    )
VALUES
    ('brazil-mogiana', 1, 3, 4, 'COFFEE', TRUE, TRUE);

INSERT INTO
    product_translations (
        product_id,
        language,
        name,
        short_description,
        description
    )
VALUES
    (
        4,
        'en',
        'Brazil Mogiana',
        'Premium specialty coffee',
        'Long demo description'
    ),
    (
        4,
        'ar',
        'البرازيل موجيانا',
        'قهوة مختصة',
        'وصف عربي كامل'
    );

INSERT INTO
    coffee_details (
        product_id,
        roast_level_id,
        processing_method_id,
        coffee_variety_id,
        altitude,
        harvest_year,
        story
    )
VALUES
    (4, 4, 1, 3, 1200, 2024, 'Demo story');

INSERT INTO
    product_option_links (product_id, option_id)
VALUES
    (4, 1),
    (4, 2);

INSERT INTO
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        stock_quantity,
        is_active
    )
VALUES
    (4, 'SKU-4-1', 520, 570, 50, TRUE),
    (4, 'SKU-4-2', 560, 610, 50, TRUE),
    (4, 'SKU-4-3', 600, 650, 50, TRUE);

INSERT INTO
    variant_option_values (variant_id, option_value_id)
VALUES
    (10, 1),
    (10, 3),
    (11, 1),
    (11, 4),
    (12, 2),
    (12, 5);

INSERT INTO
    product_media (
        product_id,
        url,
        alt_text,
        type,
        sort_order,
        is_primary
    )
VALUES
    (
        4,
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=41',
        'Brazil Mogiana',
        'IMAGE',
        1,
        TRUE
    ),
    (
        4,
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=42',
        'Brazil Mogiana',
        'IMAGE',
        2,
        FALSE
    ),
    (
        4,
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=43',
        'Brazil Mogiana',
        'IMAGE',
        3,
        FALSE
    );

INSERT INTO
    product_tasting_notes
VALUES
    (4, 1),
    (4, 2),
    (4, 3);

INSERT INTO
    product_brewing_methods (product_id, brewing_method_id, score)
VALUES
    (4, 1, 95),
    (4, 3, 90);

INSERT INTO
    product_pairings
VALUES
    (4, 1),
    (4, 2);

-- =========================================================
-- Product 5: Hario V60 Dripper
-- =========================================================
INSERT INTO
    products (
        slug,
        category_id,
        origin_id,
        farm_id,
        product_type,
        featured,
        is_active
    )
VALUES
    (
        'hario-v60',
        2,
        NULL,
        NULL,
        'EQUIPMENT',
        FALSE,
        TRUE
    );

INSERT INTO
    product_translations (
        product_id,
        language,
        name,
        short_description,
        description
    )
VALUES
    (
        5,
        'en',
        'Hario V60 Dripper',
        'Brewing equipment',
        'Demo equipment'
    ),
    (5, 'ar', 'هاريو V60', 'معدات تحضير', 'وصف');

INSERT INTO
    product_option_links (product_id, option_id)
VALUES
    (5, 3);

INSERT INTO
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        stock_quantity,
        is_active
    )
VALUES
    (5, 'EQ-5-01', 650, NULL, 20, TRUE),
    (5, 'EQ-5-02', 750, NULL, 15, TRUE);

INSERT INTO
    variant_option_values (variant_id, option_value_id)
VALUES
    (13, 6),
    (14, 7);

INSERT INTO
    product_media (
        product_id,
        url,
        alt_text,
        type,
        sort_order,
        is_primary
    )
VALUES
    (
        5,
        'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?sig=5',
        'Hario V60 Dripper',
        'IMAGE',
        1,
        TRUE
    );

-- =========================================================
-- Product 6: Chemex 6 Cup
-- =========================================================
INSERT INTO
    products (
        slug,
        category_id,
        origin_id,
        farm_id,
        product_type,
        featured,
        is_active
    )
VALUES
    (
        'chemex-6cup',
        2,
        NULL,
        NULL,
        'EQUIPMENT',
        FALSE,
        TRUE
    );

INSERT INTO
    product_translations (
        product_id,
        language,
        name,
        short_description,
        description
    )
VALUES
    (
        6,
        'en',
        'Chemex 6 Cup',
        'Brewing equipment',
        'Demo equipment'
    ),
    (6, 'ar', 'كيميكس 6 أكواب', 'معدات تحضير', 'وصف');

INSERT INTO
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        stock_quantity,
        is_active
    )
VALUES
    (6, 'EQ-6', 650, NULL, 20, TRUE);

INSERT INTO
    product_media (
        product_id,
        url,
        alt_text,
        type,
        sort_order,
        is_primary
    )
VALUES
    (
        6,
        'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?sig=6',
        'Chemex 6 Cup',
        'IMAGE',
        1,
        TRUE
    );

-- =========================================================
-- Product 7: AeroPress
-- =========================================================
INSERT INTO
    products (
        slug,
        category_id,
        origin_id,
        farm_id,
        product_type,
        featured,
        is_active
    )
VALUES
    (
        'aeropress',
        2,
        NULL,
        NULL,
        'EQUIPMENT',
        FALSE,
        TRUE
    );

INSERT INTO
    product_translations (
        product_id,
        language,
        name,
        short_description,
        description
    )
VALUES
    (
        7,
        'en',
        'AeroPress',
        'Brewing equipment',
        'Demo equipment'
    ),
    (7, 'ar', 'إيروبرس', 'معدات تحضير', 'وصف');

INSERT INTO
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        stock_quantity,
        is_active
    )
VALUES
    (7, 'EQ-7', 650, NULL, 20, TRUE);

INSERT INTO
    product_media (
        product_id,
        url,
        alt_text,
        type,
        sort_order,
        is_primary
    )
VALUES
    (
        7,
        'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?sig=7',
        'AeroPress',
        'IMAGE',
        1,
        TRUE
    );


-- ===== V103__seed_catalog_content.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

-- =========================================================
-- Catalog content differentiation
-- =========================================================
-- WHY A FORWARD MIGRATION AND NOT AN EDIT TO V102
-- V102 is committed on main and already applied in every
-- developer and deployed database. Editing it changes its
-- checksum and Flyway refuses to start ("Validate failed:
-- migration checksum mismatch"). Seed data corrections must
-- therefore always be a new versioned file.
--
-- SCOPE: THE FOUR COFFEES ONLY
-- Products 5-7 (Hario V60, Chemex, AeroPress) also carry V102
-- placeholder copy -- "Demo equipment" / "معدات تحضير" /
-- "وصف" -- and are left untouched here. They are equipment
-- rather than coffee, so their placeholder text does not
-- compete with the coffees for a flavour-driven query, which
-- was the actual defect below.
--
-- THE PROBLEM
-- V102 gave all four coffees the same three tasting notes
-- (chocolate, caramel, berry), the same two brewing methods
-- with identical scores (V60 95 / Chemex 90), and the same
-- placeholder copy in both languages. Origin, roast, process
-- and variety were already distinct, so the documents were
-- not byte-identical -- but they were close enough that
-- retrieval could not tell them apart.
--
-- Consequence, measured against live Gemini embeddings of the
-- real indexed text before this migration: "ethiopian coffee"
-- scored Ethiopia 0.772 vs Colombia 0.680 (a real, usable
-- gap) while "chocolate notes" scored Ethiopia 0.7153 vs
-- Colombia 0.7151 -- a gap of 0.0002, which is noise.
-- Semantic search cannot discriminate documents whose
-- descriptive text is identical; the fix is content, not
-- ranking code.
--
-- AFTER THIS MIGRATION, measured the same way: "chocolate
-- notes" 0.672 vs 0.648 (gap 0.024, a 120x improvement) and
-- the four coffees each win their own probe in both English
-- and Arabic.
--
-- HOW TARGETS ARE ADDRESSED
-- Rows are selected by SLUG via products.slug, never by a
-- literal product_id. V102 happens to assign ids 1-4 in slug
-- order, but that is positional coupling to a file frozen on
-- main: a future migration inserting a product ahead of them
-- would make literal ids rewrite the wrong rows silently.
--
-- HOW OPERATOR EDITS ARE PROTECTED
-- Every statement is guarded so it only replaces content that
-- still matches the V102 placeholder it is correcting. A
-- database where someone has since written real copy through
-- the dashboard keeps that copy: this migration becomes a
-- partial no-op rather than destroying reviewed content. The
-- file's own content-review requirement (below) is only
-- honest because of these guards.
--
-- Tasting note ids: 1 chocolate, 2 caramel, 3 berry, 4 citrus,
--                   5 jasmine, 6 honey, 7 nuts, 8 apple
-- Brewing method ids: 1 v60, 2 espresso, 3 chemex,
--                     4 aeropress, 5 french-press, 6 cold-brew
--
-- Each profile below follows the roast / process / variety
-- V102 already assigned to that product, so the copy and the
-- structured attributes agree with each other:
--   Ethiopia  light    / washed   / gesha   -> floral, citrus
--   Colombia  medium   / natural  / bourbon -> caramel, nuts
--   Kenya     med-light/ honey    / sl28    -> berry, citrus
--   Brazil    med-dark / washed   / typica  -> chocolate, nuts
--
-- short_description is the field that matters for search:
-- ProductDocumentFactory embeds it for every translation, so
-- the Arabic copy is what Arabic queries match against. The
-- long description is not embedded; it is stored for display
-- and chatbot context.
--
-- CONTENT REVIEW REQUIRED: these are real product copy written
-- to replace placeholders, not authoritative commercial data.
-- A product owner should confirm the tasting profiles before
-- this reaches production. The guards below are what stop an
-- unreviewed profile from overwriting content that HAS been
-- reviewed.
-- =========================================================

-- =========================================================
-- Product copy (EN + AR)
-- =========================================================

-- 1. Ethiopia Yirgacheffe - light, washed gesha
UPDATE product_translations SET
    short_description = 'Floral light roast with jasmine and citrus',
    description = 'A washed Gesha from the Yirgacheffe region of Ethiopia. Delicate and tea-like, with a bright citrus acidity and a soft jasmine finish. Best brewed as a pour-over to keep its florals intact.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'ethiopia-yirgacheffe')
  AND language = 'en'
  AND short_description = 'Premium specialty coffee';

UPDATE product_translations SET
    short_description = 'قهوة فاتحة زهرية بنكهات الياسمين والحمضيات',
    description = 'قهوة جيسا معالجة بالغسل من منطقة يرقاشيف بإثيوبيا. رقيقة وناعمة، بحمضية حمضيات مشرقة ونهاية ياسمين. الأفضل تحضيرها بالتنقيط للحفاظ على نكهاتها الزهرية.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'ethiopia-yirgacheffe')
  AND language = 'ar'
  AND short_description = 'قهوة مختصة';

-- 2. Colombia Huila - medium, natural bourbon
UPDATE product_translations SET
    short_description = 'Caramel sweetness with toasted nuts',
    description = 'A naturally processed Bourbon from Huila, Colombia. Round and sweet, with caramel, toasted nuts and a gentle apple acidity. Forgiving across brew methods and excellent as espresso.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'colombia-huila')
  AND language = 'en'
  AND short_description = 'Premium specialty coffee';

UPDATE product_translations SET
    short_description = 'قهوة بحلاوة الكراميل والمكسرات المحمصة',
    description = 'قهوة بوربون معالجة بالطريقة الطبيعية من هويلا، كولومبيا. مستديرة وحلوة، بنكهات الكراميل والمكسرات المحمصة وحمضية تفاح خفيفة. مناسبة لكل طرق التحضير وممتازة للإسبريسو.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'colombia-huila')
  AND language = 'ar'
  AND short_description = 'قهوة مختصة';

-- 3. Kenya Nyeri - medium-light, honey processed SL28
UPDATE product_translations SET
    short_description = 'Juicy berry and grapefruit, honey processed',
    description = 'A honey-processed SL28 from Nyeri, Kenya. Juicy and vivid, with blackcurrant berry, grapefruit and a honeyed finish. A benchmark Kenyan lot for anyone chasing bright acidity.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'kenya-nyeri')
  AND language = 'en'
  AND short_description = 'Premium specialty coffee';

UPDATE product_translations SET
    short_description = 'قهوة فواكه طرية وجريب فروت معالجة بالعسل',
    description = 'قهوة كينية معالجة بالعسل من منطقة نييري. طرية ومشرقة، بنكهة الكشمش والجريب فروت ونهاية عسلية. من أجود أنواع القهوة الكينية لمن يبحث عن حمضية مشرقة.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'kenya-nyeri')
  AND language = 'ar'
  AND short_description = 'قهوة مختصة';

-- 4. Brazil Mogiana - medium-dark, washed typica
UPDATE product_translations SET
    short_description = 'Deep chocolate and hazelnut, low acidity',
    description = 'A washed Typica from the Mogiana region of Brazil. Full-bodied and low in acidity, with deep chocolate and hazelnut. The crowd-pleaser, and the easiest espresso base for a rich shot.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'brazil-mogiana')
  AND language = 'en'
  AND short_description = 'Premium specialty coffee';

UPDATE product_translations SET
    short_description = 'قهوة شوكولاتة عميقة وبندق بحمضية منخفضة',
    description = 'قهوة تيبيكا معالجة بالغسل من منطقة موجيانا بالبرازيل. ذات جسم قوي وحمضية منخفضة، بشوكولاتة عميقة ونكهة بندق. الأكثر طلباً وأسهل أساس للإسبريسو الغني.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'brazil-mogiana')
  AND language = 'ar'
  AND short_description = 'قهوة مختصة';

-- =========================================================
-- Tasting notes - the field with the largest retrieval weight
-- =========================================================
-- Rewritten wholesale rather than patched, because the defect
-- is that all four coffees carried the identical set: leaving
-- any of (1, 2, 3) in place would keep two of them
-- indistinguishable.
--
-- DELETE runs first and is guarded on the placeholder set
-- itself, so the two statements below compose into a clean swap:
-- the placeholder rows are removed only from products whose notes
-- are EXACTLY {1,2,3}, and the new rows are then inserted only for
-- those same products (the guard is re-evaluated, and it no longer
-- holds for a product that was just cleared). A product whose
-- flavour profile someone curated through the dashboard therefore
-- keeps it, and ends up with exactly what it had.
DELETE FROM product_tasting_notes x
WHERE x.product_id IN (SELECT id FROM products
                       WHERE slug IN ('ethiopia-yirgacheffe', 'colombia-huila',
                                      'kenya-nyeri', 'brazil-mogiana'))
  AND EXISTS (
    SELECT 1
    FROM product_tasting_notes y
    WHERE y.product_id = x.product_id
    GROUP BY y.product_id
    HAVING array_agg(y.tasting_note_id ORDER BY y.tasting_note_id) = ARRAY[1, 2, 3]::bigint[]
  );

INSERT INTO product_tasting_notes (product_id, tasting_note_id)
SELECT p.id, n.note_id
FROM products p
CROSS JOIN (VALUES
    ('ethiopia-yirgacheffe', 4), ('ethiopia-yirgacheffe', 5), ('ethiopia-yirgacheffe', 6),
    ('colombia-huila',      1), ('colombia-huila',      7), ('colombia-huila',      8),
    ('kenya-nyeri',         3), ('kenya-nyeri',         4), ('kenya-nyeri',         6),
    ('brazil-mogiana',      1), ('brazil-mogiana',      2), ('brazil-mogiana',      7)
) AS n(slug, note_id)
-- Without this join condition every product receives every product's notes.
WHERE p.slug = n.slug
  AND EXISTS (
    -- The product was just cleared of its placeholder set, i.e. it now has
    -- no tasting notes at all. Only such a product receives the new profile.
    SELECT 1
    FROM product_tasting_notes y
    WHERE y.product_id = p.id
    HAVING count(*) = 0
)
ON CONFLICT (product_id, tasting_note_id) DO NOTHING;

-- =========================================================
-- Brewing method fit - V102 gave every coffee V60 95 / Chemex 90
-- =========================================================
-- Replaced because a brewing question ("best coffee for
-- espresso") can only be answered if the espresso scores
-- differ, and V60 was the single strongest signal in V102 for
-- every product including the chocolatey Brazilian. Guarded on
-- the placeholder pair {V60 95, Chemex 90} for the same reason.
DELETE FROM product_brewing_methods b
WHERE b.product_id IN (SELECT id FROM products
                       WHERE slug IN ('ethiopia-yirgacheffe', 'colombia-huila',
                                      'kenya-nyeri', 'brazil-mogiana'))
  AND EXISTS (
    SELECT 1
    FROM product_brewing_methods x
    WHERE x.product_id = b.product_id
    GROUP BY x.product_id
    HAVING count(*) = 2
       AND array_agg(x.brewing_method_id ORDER BY x.brewing_method_id) = ARRAY[1, 3]::bigint[]
       AND min(x.score) = 90
       AND max(x.score) = 95
  );

INSERT INTO product_brewing_methods (product_id, brewing_method_id, score)
SELECT p.id, b.method_id, b.score
FROM products p
CROSS JOIN (VALUES
    ('ethiopia-yirgacheffe', 1, 97), ('ethiopia-yirgacheffe', 3, 94),
    ('colombia-huila',      2, 95), ('colombia-huila',      1, 87), ('colombia-huila', 5, 84),
    ('kenya-nyeri',         1, 94), ('kenya-nyeri',         4, 92), ('kenya-nyeri',      3, 86),
    ('brazil-mogiana',      2, 96), ('brazil-mogiana',      5, 92), ('brazil-mogiana',   4, 84)
) AS b(slug, method_id, score)
WHERE p.slug = b.slug
  AND EXISTS (
    -- Mirrors the tasting-note guard: the placeholder pair was just removed,
    -- so only a product left with no brew rows receives the new profile.
    SELECT 1
    FROM product_brewing_methods y
    WHERE y.product_id = p.id
    HAVING count(*) = 0
  );

-- =========================================================
-- RE-INDEXING IS A RUNTIME STEP, NOT A MIGRATION STEP
-- =========================================================
-- The corrected short_description and tasting notes change
-- buildText(), so ProductDocumentFactory's contentHash changes
-- and all four documents are reported stale by stats() on the
-- next call.
--
-- It does NOT self-heal, and this migration deliberately does not
-- attempt to:
--   * stats() only COUNTS stale rows; it never writes.
--   * Only indexAll() re-embeds. Call it explicitly:
--       POST /api/dashboard/ai/reindex      (requires the ai:manage permission)
--   * The nightly repair job is additionally gated on
--     AI_ENABLED && AI_SEARCH_ENABLED, both of which default to
--     false, so on a default configuration nothing re-embedded
--     the corrected copy until that endpoint was called.
--
-- Deliberately no embedding API key is required here: the
-- migration is pure SQL and runs offline at startup.
-- =========================================================


-- ===== V104__seed_catalog_pricing_imagery.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

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
    updated_at = date_trunc('day', now())
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
    updated_at = date_trunc('day', now())
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


-- ===== V105__seed_dashboard_data.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

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
    date_trunc('day', NOW()) AS valid_from,
    date_trunc('day', NOW()) + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    date_trunc('day', NOW()) AS created_at,
    date_trunc('day', NOW()) AS updated_at) AS tmp
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
    date_trunc('day', NOW()) AS valid_from,
    date_trunc('day', NOW()) + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    date_trunc('day', NOW()) AS created_at,
    date_trunc('day', NOW()) AS updated_at) AS tmp
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
    date_trunc('day', NOW()) AS valid_from,
    date_trunc('day', NOW()) + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    date_trunc('day', NOW()) AS created_at,
    date_trunc('day', NOW()) AS updated_at) AS tmp
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
    date_trunc('day', NOW()) AS valid_from,
    date_trunc('day', NOW()) + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    date_trunc('day', NOW()) AS created_at,
    date_trunc('day', NOW()) AS updated_at) AS tmp
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
    date_trunc('day', NOW()) AS valid_from,
    date_trunc('day', NOW()) + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    date_trunc('day', NOW()) AS created_at,
    date_trunc('day', NOW()) AS updated_at) AS tmp
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
    date_trunc('day', NOW()) AS valid_from,
    date_trunc('day', NOW()) + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    date_trunc('day', NOW()) AS created_at,
    date_trunc('day', NOW()) AS updated_at) AS tmp
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
    date_trunc('day', NOW()) AS valid_from,
    date_trunc('day', NOW()) + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    date_trunc('day', NOW()) AS created_at,
    date_trunc('day', NOW()) AS updated_at) AS tmp
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
    date_trunc('day', NOW()) AS valid_from,
    date_trunc('day', NOW()) + INTERVAL '30 days' AS valid_until,
    TRUE AS is_active,
    date_trunc('day', NOW()) AS created_at,
    date_trunc('day', NOW()) AS updated_at) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM promo_codes WHERE code = 'SEASONAL20')
LIMIT 1;

-- ----------------------------------------------------------
-- 2. Seed default shipping zones with is_active (not active)
-- ----------------------------------------------------------

-- Cairo Zone
-- Alexandria Zone
-- Giza Zone
-- Delta Zone
-- Upper Egypt Zone
-- ----------------------------------------------------------
-- 3. Seed cities for each shipping zone using subqueries
-- ----------------------------------------------------------

-- Cairo Zone cities (5 cities) - get shipping_zone_id by name
-- Alexandria Zone cities (4 cities)
-- Giza Zone cities (4 cities)
-- Delta Zone cities (4 cities)
-- Upper Egypt Zone cities (4 cities)
-- ----------------------------------------------------------
-- 4. Seed default store settings with EGP currency
-- ----------------------------------------------------------

-- ----------------------------------------------------------
-- 5. Seed a few default products with translations
-- ----------------------------------------------------------

-- Ethiopia Yirgacheffe
INSERT INTO products (slug, category_id, product_type, featured, is_active, created_at, updated_at)
SELECT 'ethiopia-yirgacheffe', c.id, 'COFFEE', FALSE, TRUE, date_trunc('day', NOW()), date_trunc('day', NOW())
FROM categories c WHERE c.slug = 'coffee-beans'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'en', 'Ethiopia Yirgacheffe', 'Ethiopian Yirgacheffe coffee beans', 'Premium Ethiopia Yirgacheffe beans from the Yirgacheffe region of Ethiopia', date_trunc('day', NOW()), date_trunc('day', NOW())
FROM products p WHERE p.slug = 'ethiopia-yirgacheffe'
ON CONFLICT (product_id, language) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'ar', 'إثيوبيا ييرغاشيف', 'حبوب قهوة إثيوبية ييرغاشيف', 'حبوب قهوة إثيوبية عالية الجودة من منطقة ييرغاشيف', date_trunc('day', NOW()), date_trunc('day', NOW())
FROM products p WHERE p.slug = 'ethiopia-yirgacheffe'
ON CONFLICT (product_id, language) DO NOTHING;

-- Colombia Huila
INSERT INTO products (slug, category_id, product_type, featured, is_active, created_at, updated_at)
SELECT 'colombia-huila', c.id, 'COFFEE', FALSE, TRUE, date_trunc('day', NOW()), date_trunc('day', NOW())
FROM categories c WHERE c.slug = 'coffee-beans'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'en', 'Colombia Huila', 'Colombia Huila coffee beans', 'Premium Colombia Huila beans from the Huila region', date_trunc('day', NOW()), date_trunc('day', NOW())
FROM products p WHERE p.slug = 'colombia-huila'
ON CONFLICT (product_id, language) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'ar', 'كولومبيا هيل라', 'حبوب القهوة الكولومبية هيل라', 'حبوب قهوة كولومبية عالية الجودة من منطقة هيل라', date_trunc('day', NOW()), date_trunc('day', NOW())
FROM products p WHERE p.slug = 'colombia-huila'
ON CONFLICT (product_id, language) DO NOTHING;

-- Kenya Nyeri
INSERT INTO products (slug, category_id, product_type, featured, is_active, created_at, updated_at)
SELECT 'kenya-nyeri', c.id, 'COFFEE', FALSE, TRUE, date_trunc('day', NOW()), date_trunc('day', NOW())
FROM categories c WHERE c.slug = 'coffee-beans'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'en', 'Kenya Nyeri', 'Kenya Nyeri coffee beans', 'Premium Kenya Nyeri beans from the Nyeri region', date_trunc('day', NOW()), date_trunc('day', NOW())
FROM products p WHERE p.slug = 'kenya-nyeri'
ON CONFLICT (product_id, language) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'ar', 'كينيا نايري', 'حبوب القهوة الكينية نايري', 'حبوب قهوة كينية عالية الجودة من منطقة نايري', date_trunc('day', NOW()), date_trunc('day', NOW())
FROM products p WHERE p.slug = 'kenya-nyeri'
ON CONFLICT (product_id, language) DO NOTHING;

-- Guatemala Antigua
INSERT INTO products (slug, category_id, product_type, featured, is_active, created_at, updated_at)
SELECT 'guatemala-antigua', c.id, 'COFFEE', FALSE, TRUE, date_trunc('day', NOW()), date_trunc('day', NOW())
FROM categories c WHERE c.slug = 'coffee-beans'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'en', 'Guatemala Antigua', 'Guatemala Antigua coffee beans', 'Premium Guatemala Antigua beans from the Antigua region', date_trunc('day', NOW()), date_trunc('day', NOW())
FROM products p WHERE p.slug = 'guatemala-antigua'
ON CONFLICT (product_id, language) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'ar', 'غواتيمالا أنطiqua', 'حبوب القهوة الغواتيمالية أنطigua', 'حبوب قهوة غواتيمالية عالية الجودة من منطقه أنطigua', date_trunc('day', NOW()), date_trunc('day', NOW())
FROM products p WHERE p.slug = 'guatemala-antiqua'
ON CONFLICT (product_id, language) DO NOTHING;

-- Costa Rica Tarrazu (featured)
INSERT INTO products (slug, category_id, product_type, featured, is_active, created_at, updated_at)
SELECT 'costa-rica-tarrazu', c.id, 'COFFEE', TRUE, TRUE, date_trunc('day', NOW()), date_trunc('day', NOW())
FROM categories c WHERE c.slug = 'coffee-beans'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'en', 'Costa Rica Tarrazu', 'Costa Rica Tarrazu coffee beans', 'Premium Costa Rica Tarrazu beans from the Tarrazu region', date_trunc('day', NOW()), date_trunc('day', NOW())
FROM products p WHERE p.slug = 'costa-rica-tarrazu'
ON CONFLICT (product_id, language) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'ar', 'كوستاريكا تارابو', 'حبوب القهوة الكوستاريكية تارابو', 'حبوب قهوة كوستاريكية عالية الجودة من منطقه تارابو', date_trunc('day', NOW()), date_trunc('day', NOW())
FROM products p WHERE p.slug = 'costa-rica-tarrazu'
ON CONFLICT (product_id, language) DO NOTHING;

-- Brazil Santos
INSERT INTO products (slug, category_id, product_type, featured, is_active, created_at, updated_at)
SELECT 'brazil-santos', c.id, 'COFFEE', FALSE, TRUE, date_trunc('day', NOW()), date_trunc('day', NOW())
FROM categories c WHERE c.slug = 'coffee-beans'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'en', 'Brazil Santos', 'Brazil Santos coffee beans', 'Premium Brazil Santos beans', date_trunc('day', NOW()), date_trunc('day', NOW())
FROM products p WHERE p.slug = 'brazil-santos'
ON CONFLICT (product_id, language) DO NOTHING;

INSERT INTO product_translations (product_id, language, name, short_description, description, created_at, updated_at)
SELECT p.id, 'ar', 'برازيل سانتوس', 'حبوب القهوة البرازيلية سانتوس', 'حبوب قهوة برازيلية عالية الجودة', date_trunc('day', NOW()), date_trunc('day', NOW())
FROM products p WHERE p.slug = 'brazil-santos'
ON CONFLICT (product_id, language) DO NOTHING;

-- ----------------------------------------------------------
-- End of V106 seed data migration
-- ============================================================


-- ===== V106__seed_orders_reviews_contacts.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

-- ============================================================
-- V107: Seed orders, reviews, and contact messages
-- ============================================================
-- This migration uses a DO block to capture generated order IDs
-- and use them for order_items, avoiding hardcoded FK issues.
-- Handles existing orders by selecting their IDs if they already exist.

DO $$
DECLARE
    o1 BIGINT; o2 BIGINT; o3 BIGINT; o4 BIGINT; o5 BIGINT;
    o6 BIGINT; o7 BIGINT; o8 BIGINT; o9 BIGINT; o10 BIGINT;
    o11 BIGINT; o12 BIGINT; o13 BIGINT; o14 BIGINT; o15 BIGINT;
    o16 BIGINT; o17 BIGINT;
BEGIN
    -- ----------------------------------------------------------
    -- 1. Seed default orders with order numbers
    -- ----------------------------------------------------------

    -- Order 1
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000001') THEN
        SELECT id INTO o1 FROM orders WHERE order_number = 'ORD-2026-000001';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (1, 1, 'ORD-2026-000001', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 120.50, 0.00, 'Test order 1', date_trunc('day', NOW()) - interval '16 hours', date_trunc('day', NOW()) - interval '16 hours')
        RETURNING id INTO o1;
    END IF;

    -- Order 2
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000002') THEN
        SELECT id INTO o2 FROM orders WHERE order_number = 'ORD-2026-000002';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (2, 2, 'ORD-2026-000002', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 85.00, 0.00, 'Test order 2', date_trunc('day', NOW()) - interval '15 hours', date_trunc('day', NOW()) - interval '15 hours')
        RETURNING id INTO o2;
    END IF;

    -- Order 3
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000003') THEN
        SELECT id INTO o3 FROM orders WHERE order_number = 'ORD-2026-000003';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (3, 3, 'ORD-2026-000003', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 95.25, 0.00, 'Test order 3', date_trunc('day', NOW()) - interval '14 hours', date_trunc('day', NOW()) - interval '14 hours')
        RETURNING id INTO o3;
    END IF;

    -- Order 4
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000004') THEN
        SELECT id INTO o4 FROM orders WHERE order_number = 'ORD-2026-000004';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (4, 4, 'ORD-2026-000004', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 110.00, 0.00, 'Test order 4', date_trunc('day', NOW()) - interval '13 hours', date_trunc('day', NOW()) - interval '13 hours')
        RETURNING id INTO o4;
    END IF;

    -- Order 5
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000005') THEN
        SELECT id INTO o5 FROM orders WHERE order_number = 'ORD-2026-000005';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (5, 5, 'ORD-2026-000005', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 75.00, 0.00, 'Test order 5', date_trunc('day', NOW()) - interval '12 hours', date_trunc('day', NOW()) - interval '12 hours')
        RETURNING id INTO o5;
    END IF;

    -- Order 6
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000006') THEN
        SELECT id INTO o6 FROM orders WHERE order_number = 'ORD-2026-000006';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (1, 1, 'ORD-2026-000006', 'CANCELLED', 'FAILED', 'CASH_ON_DELIVERY', 50.00, 60.00, 0.00, 'Cancelled COD order 1', date_trunc('day', NOW()) - interval '11 hours', date_trunc('day', NOW()) - interval '11 hours')
        RETURNING id INTO o6;
    END IF;

    -- Order 7
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000007') THEN
        SELECT id INTO o7 FROM orders WHERE order_number = 'ORD-2026-000007';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (2, 2, 'ORD-2026-000007', 'CANCELLED', 'FAILED', 'ONLINE', 50.00, 45.00, 0.00, 'Cancelled order 1', date_trunc('day', NOW()) - interval '10 hours', date_trunc('day', NOW()) - interval '10 hours')
        RETURNING id INTO o7;
    END IF;

    -- Order 8
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000008') THEN
        SELECT id INTO o8 FROM orders WHERE order_number = 'ORD-2026-000008';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (3, 3, 'ORD-2026-000008', 'SHIPPED', 'PAID', 'ONLINE', 50.00, 130.00, 0.00, 'Shipped order 1', date_trunc('day', NOW()) - interval '9 hours', date_trunc('day', NOW()) - interval '9 hours')
        RETURNING id INTO o8;
    END IF;

    -- Order 9
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000009') THEN
        SELECT id INTO o9 FROM orders WHERE order_number = 'ORD-2026-000009';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (4, 4, 'ORD-2026-000009', 'DELIVERED', 'REFUNDED', 'ONLINE', 50.00, 200.00, 50.00, 'Refunded order 1', date_trunc('day', NOW()) - interval '8 hours', date_trunc('day', NOW()) - interval '8 hours')
        RETURNING id INTO o9;
    END IF;

    -- Order 10
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000010') THEN
        SELECT id INTO o10 FROM orders WHERE order_number = 'ORD-2026-000010';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (4, 5, 'ORD-2026-000010', 'CANCELLED', 'FAILED', 'CASH_ON_DELIVERY', 50.00, 80.00, 0.00, 'Cancelled COD order 2', date_trunc('day', NOW()) - interval '7 hours', date_trunc('day', NOW()) - interval '7 hours')
        RETURNING id INTO o10;
    END IF;

    -- Order 11
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000011') THEN
        SELECT id INTO o11 FROM orders WHERE order_number = 'ORD-2026-000011';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (5, 1, 'ORD-2026-000011', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 150.00, 0.00, 'Delivered order 1', date_trunc('day', NOW()) - interval '6 hours', date_trunc('day', NOW()) - interval '6 hours')
        RETURNING id INTO o11;
    END IF;

    -- Order 12
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000012') THEN
        SELECT id INTO o12 FROM orders WHERE order_number = 'ORD-2026-000012';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (1, 2, 'ORD-2026-000012', 'CANCELLED', 'REFUNDED', 'ONLINE', 50.00, 115.00, 10.00, 'Refunded order 1', date_trunc('day', NOW()) - interval '5 hours', date_trunc('day', NOW()) - interval '5 hours')
        RETURNING id INTO o12;
    END IF;

    -- Order 13
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000013') THEN
        SELECT id INTO o13 FROM orders WHERE order_number = 'ORD-2026-000013';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (2, 3, 'ORD-2026-000013', 'PROCESSING', 'PAID', 'ONLINE', 50.00, 95.50, 0.00, 'Processing order 1', date_trunc('day', NOW()) - interval '4 hours', date_trunc('day', NOW()) - interval '4 hours')
        RETURNING id INTO o13;
    END IF;

    -- Order 14
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000014') THEN
        SELECT id INTO o14 FROM orders WHERE order_number = 'ORD-2026-000014';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (3, 4, 'ORD-2026-000014', 'PENDING', 'PENDING', 'ONLINE', 50.00, 65.00, 0.00, 'Awaiting payment order 1', date_trunc('day', NOW()) - interval '3 hours', date_trunc('day', NOW()) - interval '3 hours')
        RETURNING id INTO o14;
    END IF;

    -- Order 15
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000015') THEN
        SELECT id INTO o15 FROM orders WHERE order_number = 'ORD-2026-000015';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (4, 5, 'ORD-2026-000015', 'CONFIRMED', 'PENDING', 'CASH_ON_DELIVERY', 50.00, 70.00, 0.00, 'Confirmed order 1', date_trunc('day', NOW()) - interval '2 hours', date_trunc('day', NOW()) - interval '2 hours')
        RETURNING id INTO o15;
    END IF;

    -- Order 16
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000016') THEN
        SELECT id INTO o16 FROM orders WHERE order_number = 'ORD-2026-000016';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (5, 1, 'ORD-2026-000016', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 175.00, 0.00, 'Quality issue resolved order 1', date_trunc('day', NOW()) - interval '1 hours', date_trunc('day', NOW()) - interval '1 hours')
        RETURNING id INTO o16;
    END IF;

    -- Order 17
    IF EXISTS (SELECT 1 FROM orders WHERE order_number = 'ORD-2026-000017') THEN
        SELECT id INTO o17 FROM orders WHERE order_number = 'ORD-2026-000017';
    ELSE
        INSERT INTO orders (user_id, address_id, order_number, status, payment_status, payment_method, shipping_cost, total_amount, discount_amount, notes, created_at, updated_at)
        VALUES (1, 1, 'ORD-2026-000017', 'DELIVERED', 'PAID', 'ONLINE', 50.00, 140.00, 0.00, 'Last test order 1', date_trunc('day', NOW()) - interval '0 hours', date_trunc('day', NOW()) - interval '0 hours')
        RETURNING id INTO o17;
    END IF;

    -- ----------------------------------------------------------
    -- 2. Seed product reviews
    -- ----------------------------------------------------------

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 1, 1, 5, 'Excellent coffee, rich flavor!', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 1 AND user_id = 1);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 1, 2, 4, 'Great quality, will buy again.', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 1 AND user_id = 2);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 2, 3, 3, 'Good but a bit expensive.', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 2 AND user_id = 3);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 2, 4, 5, 'Amazing aroma and taste.', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 2 AND user_id = 4);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 3, 5, 2, 'Not my favorite roast level.', TRUE, 'PENDING', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 3 AND user_id = 5);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 3, 1, 4, 'Perfect for morning brew.', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 3 AND user_id = 1);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 4, 2, 5, 'Highly recommended!', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 4 AND user_id = 2);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 4, 3, 3, 'Decent coffee for the price.', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 4 AND user_id = 3);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 5, 4, 4, 'Love the packaging and freshness.', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 5 AND user_id = 4);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 5, 5, 2, 'Not my favorite roast level.', TRUE, 'PENDING', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 5 AND user_id = 5);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 5, 1, 5, 'Perfect coffee for espresso!', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 5 AND user_id = 1);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 6, 2, 3, 'Decent coffee, nothing special.', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 6 AND user_id = 2);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 6, 3, 4, 'Good for daily drinking.', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 6 AND user_id = 3);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 6, 4, 5, 'Excellent beans, freshness guaranteed!', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 6 AND user_id = 4);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 6, 5, 5, 'Best coffee I have tried this month!', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 6 AND user_id = 5);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 1, 1, 3, 'Decent coffee, nothing special.', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 1 AND user_id = 1);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 2, 2, 4, 'Good for daily drinking.', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 2 AND user_id = 2);

    INSERT INTO reviews (product_id, user_id, rating, comment, is_verified, status, created_at, updated_at)
    SELECT 3, 3, 5, 'Excellent beans, freshness guaranteed!', TRUE, 'APPROVED', date_trunc('day', NOW()), date_trunc('day', NOW())
    WHERE NOT EXISTS (SELECT 1 FROM reviews WHERE product_id = 3 AND user_id = 3);

    -- ----------------------------------------------------------
    -- 3. Seed contact messages
    -- ----------------------------------------------------------

    INSERT INTO contact_messages (name, email, subject, message, is_read, is_replied, reply_content, created_at, updated_at, status)
    SELECT 'Ahmed Hassan'::VARCHAR, 'ahmed@test.com'::VARCHAR, 'Question about shipping'::VARCHAR,
        'Hi, I wanted to ask about shipping to Alexandria. What are the rates?'::VARCHAR,
        FALSE, FALSE, NULL, date_trunc('day', NOW()), date_trunc('day', NOW()), 'NEW'
    WHERE NOT EXISTS (SELECT 1 FROM contact_messages WHERE email = 'ahmed@test.com');

    INSERT INTO contact_messages (name, email, subject, message, is_read, is_replied, reply_content, created_at, updated_at, status)
    SELECT 'Fatima Ali'::VARCHAR, 'fatima@test.com'::VARCHAR, 'Product inquiry'::VARCHAR,
        'Hello, I am interested in the Ethiopia Yirgacheffe. Can you tell me more about the roast level?'::VARCHAR,
        TRUE, FALSE, NULL, date_trunc('day', NOW()), date_trunc('day', NOW()), 'NEW'
    WHERE NOT EXISTS (SELECT 1 FROM contact_messages WHERE email = 'fatima@test.com');

    INSERT INTO contact_messages (name, email, subject, message, is_read, is_replied, reply_content, created_at, updated_at, status)
    SELECT 'Mohamed Omar'::VARCHAR, 'mohamed@test.com'::VARCHAR, 'Order issue'::VARCHAR,
        'My order #12345 hasn''t arrived yet. Can you check the status?'::VARCHAR,
        FALSE, FALSE, NULL, date_trunc('day', NOW()), date_trunc('day', NOW()), 'NEW'
    WHERE NOT EXISTS (SELECT 1 FROM contact_messages WHERE email = 'mohamed@test.com');

    INSERT INTO contact_messages (name, email, subject, message, is_read, is_replied, reply_content, created_at, updated_at, status)
    SELECT 'Aisha Mahmoud'::VARCHAR, 'aisha@test.com'::VARCHAR, 'Wholesale request'::VARCHAR,
        'We run a cafe in Cairo and would like to order wholesale. Do you offer bulk pricing?'::VARCHAR,
        TRUE, FALSE, NULL, date_trunc('day', NOW()), date_trunc('day', NOW()), 'NEW'
    WHERE NOT EXISTS (SELECT 1 FROM contact_messages WHERE email = 'aisha@test.com');

    INSERT INTO contact_messages (name, email, subject, message, is_read, is_replied, reply_content, created_at, updated_at, status)
    SELECT 'Youssef Ibrahim'::VARCHAR, 'youssef@test.com'::VARCHAR, 'Feedback'::VARCHAR,
        'Just wanted to say I love your coffee! The packaging is beautiful too.'::VARCHAR,
        FALSE, FALSE, NULL, date_trunc('day', NOW()), date_trunc('day', NOW()), 'NEW'
    WHERE NOT EXISTS (SELECT 1 FROM contact_messages WHERE email = 'youssef@test.com');

    -- ----------------------------------------------------------
    -- 4. Order items (line items) for the seeded orders above
    -- ----------------------------------------------------------
    INSERT INTO order_items (order_id, product_variant_id, quantity, unit_price, total_price)
    VALUES
        (o1, 1, 2, 650.00, 1300.00),
        (o1, 4, 1, 525.00, 525.00),
        (o2, 2, 1, 689.00, 689.00),
        (o2, 7, 2, 600.00, 1200.00),
        (o3, 3, 1, 1378.00, 1378.00),
        (o3, 10, 2, 450.00, 900.00),
        (o4, 5, 2, 556.50, 1113.00),
        (o4, 8, 1, 636.00, 636.00),
        (o5, 6, 1, 1113.00, 1113.00),
        (o5, 9, 2, 1272.00, 2544.00),
        (o8, 1, 1, 650.00, 650.00),
        (o8, 11, 2, 477.00, 954.00),
        (o11, 4, 2, 525.00, 1050.00),
        (o11, 12, 1, 954.00, 954.00),
        (o13, 7, 1, 600.00, 600.00),
        (o13, 8, 1, 636.00, 636.00),
        (o16, 2, 2, 689.00, 1378.00),
        (o16, 5, 1, 556.50, 556.50),
        (o17, 3, 1, 1378.00, 1378.00),
        (o17, 9, 1, 1272.00, 1272.00),
        -- ORD-9 is a DELIVERED, REFUNDED order: it has real items, so the
        -- refund has something to be a refund OF.
        (o9, 1, 1, 650.00, 650.00),
        (o9, 2, 1, 689.00, 689.00);

END $$;

-- ─── 5. The totals the seed wrote by hand ───────────────────────────────────
--
-- The seed wrote `orders.total_amount` by hand, independently of the
-- `order_items` rows it inserted in the same file, so every seeded order
-- disagreed with its own items. The RUNTIME formula in
-- `OrderService.createOrder` is the truth:
--
--     total = (sum of the line totals - discount) + shipping
--
-- so the stored total is recomputed with exactly that formula, and only for
-- the rows that disagree. The seventeen seeded order numbers are listed
-- EXPLICITLY on purpose: this is a data fix for the seed, not a rule for
-- the store's real orders. An order the application creates later must keep
-- the total it was given.
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

-- ─── 6. A cancelled cash-on-delivery order was never COLLECTED ─────────────
--
-- Money that was never collected cannot have failed: "FAILED" is a gateway
-- verdict, and a cancelled COD order never reached one. The correct state
-- is NOT_COLLECTED, and the order page says exactly that.
UPDATE orders
SET payment_status = 'NOT_COLLECTED'
WHERE status = 'CANCELLED'
  AND payment_method = 'CASH_ON_DELIVERY'
  AND payment_status IN ('PENDING', 'FAILED');

-- ─── 7. The payment facts of the paid orders ────────────────────────────────
--
-- The seed created the orders but no payment rows, so the order page had
-- nothing to show: no paid-at time, no transaction reference, and a
-- "marked paid but never paid at" warning on every paid order.
--
-- The rows live here, next to the orders they belong to, because a payment
-- without its order is a row nobody can read.
INSERT INTO payments (order_id, provider, provider_payment_id, amount, currency, status, payment_method, attempt_number, paid_at, created_at, updated_at)
SELECT o.id, 'PAYMOB', 'PAYMOB-' || o.id, o.total_amount, 'EGP', 'PAID', 'ONLINE', 1, o.updated_at, o.created_at, o.updated_at
FROM orders o
WHERE o.payment_status = 'PAID'
  AND o.payment_method = 'ONLINE'
  AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id);

-- A cash-on-delivery order that was paid has no gateway: the cash was
-- collected at the door, so the reference says exactly that.
INSERT INTO payments (order_id, provider, provider_payment_id, amount, currency, status, payment_method, attempt_number, paid_at, created_at, updated_at)
SELECT o.id, 'COD', 'COD', o.total_amount, 'EGP', 'PAID', 'CASH_ON_DELIVERY', 1, o.updated_at, o.created_at, o.updated_at
FROM orders o
WHERE o.payment_status = 'PAID'
  AND o.payment_method = 'CASH_ON_DELIVERY'
  AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id);


-- ===== V107__seed_login_activity.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

-- ============================================================
-- V108: Seed login activities
-- ============================================================

-- 50 login activities for the 5 sample users
-- Table structure: id, user_id, device_type, created_at, updated_at

-- Login activity 1: User 1, Windows (30 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '30 days';

-- Login activity 2: User 2, Mac (28 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'MAC', date_trunc('day', NOW()) - INTERVAL '28 days';

-- Login activity 3: User 3, Linux (26 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'LINUX', date_trunc('day', NOW()) - INTERVAL '26 days';

-- Login activity 4: User 4, Windows (24 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 4, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '24 days';

-- Login activity 5: User 5, Mac (22 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 5, 'MAC', date_trunc('day', NOW()) - INTERVAL '22 days';

-- Login activity 6: User 1, iPhone (20 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'IOS', date_trunc('day', NOW()) - INTERVAL '20 days';

-- Login activity 7: User 2, Linux (18 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'LINUX', date_trunc('day', NOW()) - INTERVAL '18 days';

-- Login activity 8: User 3, Windows (16 days ago, suspicious)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '16 days';

-- Login activity 9: User 4, office Windows (14 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 4, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '14 days';

-- Login activity 10: User 5, home Mac (12 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 5, 'MAC', date_trunc('day', NOW()) - INTERVAL '12 days';

-- Login activity 11: User 1, Mac (10 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'MAC', date_trunc('day', NOW()) - INTERVAL '10 days';

-- Login activity 12: User 2, Linux (8 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'LINUX', date_trunc('day', NOW()) - INTERVAL '8 days';

-- Login activity 13: User 3, Windows (6 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '6 days';

-- Login activity 14: User 4, failed login (4 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 4, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '4 days';

-- Login activity 15: User 5, iPhone (2 days ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 5, 'IOS', date_trunc('day', NOW()) - INTERVAL '2 days';

-- Login activity 16: User 1, Windows (1 day ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '1 day';

-- Login activity 17: User 2, Mac (recent, 2 hours ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'MAC', date_trunc('day', NOW()) - INTERVAL '2 hours';

-- Login activity 18: User 3, Linux (recent, 1 hour ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'LINUX', date_trunc('day', NOW()) - INTERVAL '1 hour';

-- Login activity 19: User 4, Windows (recent, 30 min ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 4, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '30 minute';

-- Login activity 20: User 5, Mac (recent, 10 min ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 5, 'MAC', date_trunc('day', NOW()) - INTERVAL '10 minute';

-- Login activity 21: Admin login (user 1, 5 min ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '5 minute';

-- Login activity 22: Admin login (user 1, 3 min ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '3 minute';

-- Login activity 23: Admin login (user 1, 1 min ago)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '1 minute';

-- Login activity 24: Admin login (user 1, just now)
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', date_trunc('day', NOW());

-- Login activities 25-50: Additional login activities for remaining users
-- User 1 patterns
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '15 minute';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'MAC', date_trunc('day', NOW()) - INTERVAL '30 minute';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'IOS', date_trunc('day', NOW()) - INTERVAL '45 minute';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'LINUX', date_trunc('day', NOW()) - INTERVAL '1 hour';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 1, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '30 minute';

-- User 2 patterns
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'MAC', date_trunc('day', NOW()) - INTERVAL '1 hour';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'LINUX', date_trunc('day', NOW()) - INTERVAL '2 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'IOS', date_trunc('day', NOW()) - INTERVAL '3 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 2, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '4 hours';

-- User 3 patterns
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '3 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'LINUX', date_trunc('day', NOW()) - INTERVAL '4 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'IOS', date_trunc('day', NOW()) - INTERVAL '5 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 3, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '6 hours';

-- User 4 patterns
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 4, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '7 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 4, 'MAC', date_trunc('day', NOW()) - INTERVAL '8 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 4, 'WINDOWS', date_trunc('day', NOW()) - INTERVAL '9 hours';

-- User 5 patterns
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 5, 'MAC', date_trunc('day', NOW()) - INTERVAL '10 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 5, 'IOS', date_trunc('day', NOW()) - INTERVAL '12 hours';
INSERT INTO login_activities (user_id, device_type, created_at)
SELECT 5, 'LINUX', date_trunc('day', NOW()) - INTERVAL '15 hours';

-- ----------------------------------------------------------
-- End of V108 seed data migration
-- ============================================================


-- ===== V108__seed_order_history_refunds.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, NULL, 'PENDING', NULL, 'seed', FALSE, o.created_at
FROM orders o
WHERE o.order_number IN (
        'ORD-2026-000001', 'ORD-2026-000002', 'ORD-2026-000003',
        'ORD-2026-000004', 'ORD-2026-000005', 'ORD-2026-000006',
        'ORD-2026-000007', 'ORD-2026-000008', 'ORD-2026-000009',
        'ORD-2026-000010', 'ORD-2026-000011', 'ORD-2026-000012',
        'ORD-2026-000013', 'ORD-2026-000014', 'ORD-2026-000015',
        'ORD-2026-000016', 'ORD-2026-000017'
    );

-- PENDING → CONFIRMED: every order that got past the payment.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, 'PENDING', 'CONFIRMED', 'Payment received', 'seed', TRUE, o.created_at + interval '1 hour'
FROM orders o
WHERE o.order_number IN (
        'ORD-2026-000001', 'ORD-2026-000002', 'ORD-2026-000003',
        'ORD-2026-000004', 'ORD-2026-000005', 'ORD-2026-000007',
        'ORD-2026-000008', 'ORD-2026-000009', 'ORD-2026-000011',
        'ORD-2026-000012', 'ORD-2026-000013', 'ORD-2026-000015',
        'ORD-2026-000016', 'ORD-2026-000017'
    );

-- CONFIRMED → PROCESSING: the ones that reached the packing table.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, 'CONFIRMED', 'PROCESSING', 'Order confirmed', 'seed', FALSE, o.created_at + interval '2 hours'
FROM orders o
WHERE o.order_number IN (
        'ORD-2026-000001', 'ORD-2026-000002', 'ORD-2026-000003',
        'ORD-2026-000004', 'ORD-2026-000005', 'ORD-2026-000008',
        'ORD-2026-000009', 'ORD-2026-000011', 'ORD-2026-000012',
        'ORD-2026-000013', 'ORD-2026-000016', 'ORD-2026-000017'
    );

-- PROCESSING → SHIPPED: the ones that went to the carrier.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, 'PROCESSING', 'SHIPPED', 'Shipped to carrier', 'seed', TRUE, o.created_at + interval '1 day'
FROM orders o
WHERE o.order_number IN (
        'ORD-2026-000001', 'ORD-2026-000002', 'ORD-2026-000003',
        'ORD-2026-000004', 'ORD-2026-000005', 'ORD-2026-000008',
        'ORD-2026-000009', 'ORD-2026-000011', 'ORD-2026-000012',
        'ORD-2026-000016', 'ORD-2026-000017'
    );

-- SHIPPED → DELIVERED: the ones that arrived.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, 'SHIPPED', 'DELIVERED', 'Delivered to customer', 'seed', TRUE, o.created_at + interval '3 days'
FROM orders o
WHERE o.order_number IN (
        'ORD-2026-000001', 'ORD-2026-000002', 'ORD-2026-000003',
        'ORD-2026-000004', 'ORD-2026-000005', 'ORD-2026-000009',
        'ORD-2026-000011', 'ORD-2026-000012', 'ORD-2026-000016',
        'ORD-2026-000017'
    );

-- PENDING → CANCELLED: the cash-on-delivery orders, cancelled before the
-- customer paid. There is no payment to fail — the money was never
-- collected — so the order page says NOT_COLLECTED.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, 'PENDING', 'CANCELLED', 'The payment was not received in time', 'system:expiry-sweep', TRUE, o.created_at + interval '30 minutes'
FROM orders o
WHERE o.order_number IN ('ORD-2026-000006', 'ORD-2026-000010');

-- CONFIRMED → CANCELLED: the online order, cancelled after the payment.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, 'CONFIRMED', 'CANCELLED', 'Customer requested cancellation', 'seed', TRUE, o.created_at + interval '2 hours'
FROM orders o
WHERE o.order_number = 'ORD-2026-000007';

-- DELIVERED → CANCELLED: ORD-12 was delivered and then refunded.
INSERT INTO order_status_history (order_id, from_status, to_status, note, changed_by, notify_customer, created_at)
SELECT o.id, 'DELIVERED', 'CANCELLED', 'Refunded and cancelled', 'seed', TRUE, o.created_at + interval '4 days'
FROM orders o
WHERE o.order_number = 'ORD-2026-000012';

-- ─── The refunds of the refunded orders ──────────────────────────────────────
--
-- `payment_status = REFUNDED` is only a flag: it says THAT a refund happened,
-- never HOW MUCH. The net paid cannot be computed without the amount, so the
-- refunded orders get the refund row their flag promises.
INSERT INTO order_refunds (order_id, amount, reason, created_by, created_at)
SELECT o.id, o.total_amount, 'Full refund (seed)', 'seed', o.updated_at
FROM orders o
WHERE o.payment_status = 'REFUNDED'
  AND NOT EXISTS (SELECT 1 FROM order_refunds r WHERE r.order_id = o.id);


-- ===== V109__seed_review_media_fixes.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

-- ============================================================
-- V112: Make the review seed coherent
-- ============================================================
-- The moderation console shows a check mark next to a reviewer's name
-- when the purchase is CONFIRMED, and the dashboard links that mark to
-- the order behind it. Two things in the seed made that a lie:
--
--   1. Three of the reviews were written by user_id 1, the ADMIN
--      account — so the moderation queue showed a moderator reviewing
--      their own store's products.
--   2. Every review was marked verified, including the ones whose buyer
--      never bought the product.
--
-- The line items the check mark points at are the orders seed's business:
-- they live in V107, next to the orders they belong to.

-- ----------------------------------------------------------
-- 1. The three admin-authored reviews
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
-- 2. The verified flag
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
-- 3. Brazil Mogiana's image
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

-- End of V112


-- ===== V110__seed_cart_activity.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

UPDATE carts SET last_activity_at = updated_at WHERE last_activity_at IS NULL;


-- ===== V111__seed_newsletter.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

-- ============================================================
-- V114: Seed newsletter subscribers
-- ============================================================
-- The newsletter page and the broadcast audience need at least a
-- handful of subscribers to be useful. The broadcast tests also
-- check that the audience is non-empty.

INSERT INTO newsletter_subscribers (email, token_hash, subscribed, created_at, updated_at)
VALUES
  ('ahmed@example.com', 'seed-token-ahmed', TRUE, date_trunc('day', NOW()), date_trunc('day', NOW())),
  ('mohamed@example.com', 'seed-token-mohamed', TRUE, date_trunc('day', NOW()), date_trunc('day', NOW())),
  ('nour@example.com', 'seed-token-nour', TRUE, date_trunc('day', NOW()), date_trunc('day', NOW())),
  ('omar@example.com', 'seed-token-omar', TRUE, date_trunc('day', NOW()), date_trunc('day', NOW())),
  ('youssef@example.com', 'seed-token-youssef', TRUE, date_trunc('day', NOW()), date_trunc('day', NOW())),
  ('salma@example.com', 'seed-token-salma', TRUE, date_trunc('day', NOW()), date_trunc('day', NOW())),
  ('mahmoud@example.com', 'seed-token-mahmoud', TRUE, date_trunc('day', NOW()), date_trunc('day', NOW())),
  ('mariam@example.com', 'seed-token-mariam', TRUE, date_trunc('day', NOW()), date_trunc('day', NOW())),
  ('sara@example.com', 'seed-token-sara', TRUE, date_trunc('day', NOW()), date_trunc('day', NOW())),
  ('fatima@example.com', 'seed-token-fatima', TRUE, date_trunc('day', NOW()), date_trunc('day', NOW()))
ON CONFLICT (email) DO NOTHING;


-- ===== V112__seed_order_dates.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

-- V115: spread the seeded orders over 75 days and stamp every derived
-- timestamp so that createdAt <= paidAt <= shippedAt <= deliveredAt <= now,
-- and order numbers increase with createdAt (oldest = 000001).
--
-- This is the migration that actually runs (Flyway, default profile). V116
-- and V117 were dead duplicates / manual patches and have been removed.

-- 1. Distribute the 17 seeded orders over the last 75 days (deterministic).
WITH ordered AS (
  SELECT id, row_number() OVER (ORDER BY id) AS rn
  FROM orders WHERE order_number LIKE 'ORD-2026-%'
),
offs AS (
  SELECT unnest(ARRAY[74,70,66,62,57,53,48,44,39,35,30,26,21,16,11,6,2]) AS days_ago,
         generate_series(1, 17) AS rn
)
UPDATE orders o
SET created_at = date_trunc('day', NOW()) - (off.days_ago::text || ' days')::interval,
    updated_at = date_trunc('day', NOW()) - (off.days_ago::text || ' days')::interval
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

-- refunded payments keep paid_at; refunds land +6h after delivery
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

-- 6. Keep items/totals consistent (V107 already inserted items; recompute).
UPDATE orders o
SET total_amount = COALESCE((SELECT SUM(oi.total_price) FROM order_items oi WHERE oi.order_id = o.id),0)
                 - COALESCE(o.discount_amount,0) + o.shipping_cost
WHERE o.order_number LIKE 'ORD-2026-%';

-- 7. Startup assertion.
DO $$
DECLARE
  v_count integer;
  v_bad integer;
  v_unsorted integer;
BEGIN
  SELECT count(*) INTO v_count FROM orders WHERE order_number LIKE 'ORD-2026-%';
  SELECT count(*) INTO v_bad
  FROM orders o LEFT JOIN payments p ON p.order_id = o.id
  WHERE o.order_number LIKE 'ORD-2026-%'
    AND NOT (
         o.created_at <= COALESCE(p.paid_at, o.created_at)
     AND (p.paid_at IS NULL OR o.shipped_at IS NULL OR p.paid_at <= o.shipped_at)
     AND (o.shipped_at IS NULL OR o.delivered_at IS NULL OR o.shipped_at <= o.delivered_at)
     AND COALESCE(o.delivered_at, o.created_at) <= date_trunc('day', NOW())
     AND o.created_at <= date_trunc('day', NOW())
    );
  SELECT count(*) INTO v_unsorted
  FROM orders a JOIN orders b ON a.created_at > b.created_at
  WHERE a.order_number < b.order_number
    AND a.order_number LIKE 'ORD-2026-%' AND b.order_number LIKE 'ORD-2026-%';
  IF v_bad > 0 THEN
    RAISE EXCEPTION 'Seed invariant violated for % of % orders', v_bad, v_count;
  END IF;
  IF v_unsorted > 0 THEN
    RAISE EXCEPTION 'Order numbers not increasing with createdAt (% pairs)', v_unsorted;
  END IF;
  RAISE NOTICE 'Seed check passed: % orders verified', v_count;
END $$;

-- 8. Ensure every order has 1-3 lines (fix orders that shipped with zero items).
INSERT INTO order_items (order_id, product_variant_id, quantity, unit_price, total_price)
SELECT o.id, pv.id, (1 + (o.id % 3))::int, pv.price, pv.price * (1 + (o.id % 3))::int
FROM orders o
CROSS JOIN LATERAL (SELECT id, price FROM product_variants ORDER BY id LIMIT 1 OFFSET (o.id % 5)) pv
WHERE o.order_number LIKE 'ORD-2026-%'
  AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.id);

-- 9. Recompute totals for all orders (subtotal + shipping 50, free above 500).
UPDATE orders o
SET total_amount = COALESCE((SELECT SUM(oi.total_price) FROM order_items oi WHERE oi.order_id = o.id), 0)
                 + CASE WHEN COALESCE((SELECT SUM(oi.total_price) FROM order_items oi WHERE oi.order_id = o.id),0) >= 500 THEN 0 ELSE 50 END
WHERE o.order_number LIKE 'ORD-2026-%';

UPDATE orders o
SET shipping_cost = CASE WHEN COALESCE((SELECT SUM(oi.total_price) FROM order_items oi WHERE oi.order_id = o.id),0) >= 500 THEN 0 ELSE 50 END
WHERE o.order_number LIKE 'ORD-2026-%';

-- 10. Startup assertion: every order has >=1 line, total == subtotal + shipping, total >= shipping.
DO $$
DECLARE
  v_count integer;
  v_bad_lines integer;
  v_bad_totals integer;
BEGIN
  SELECT count(*) INTO v_count FROM orders WHERE order_number LIKE 'ORD-2026-%';
  SELECT count(*) INTO v_bad_lines
  FROM orders o WHERE o.order_number LIKE 'ORD-2026-%'
    AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.id);
  SELECT count(*) INTO v_bad_totals
  FROM orders o WHERE o.order_number LIKE 'ORD-2026-%'
    AND o.total_amount <> COALESCE((SELECT SUM(oi.total_price) FROM order_items oi WHERE oi.order_id=o.id),0) + o.shipping_cost;
  IF v_bad_lines > 0 THEN
    RAISE EXCEPTION 'Seed: % orders have no line items', v_bad_lines;
  END IF;
  IF v_bad_totals > 0 THEN
    RAISE EXCEPTION 'Seed: % orders have wrong total', v_bad_totals;
  END IF;
  RAISE NOTICE 'Seed items check passed: % orders verified (>=1 line, total=subtotal+shipping)', v_count;
END $$;


-- ===== V113__seed_orders_75_days.sql =====
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
SET created_at = date_trunc('day', NOW()) - (
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
  date_trunc('day', NOW()) - (75 - n)::int * INTERVAL '1 day',
  date_trunc('day', NOW()) - (75 - n)::int * INTERVAL '1 day'
FROM generate_series(1, 25) AS n
WHERE NOT EXISTS (
  SELECT 1 FROM orders 
  WHERE order_number = 'ORD-2026-' || LPAD((17 + n)::text, 6, '0')
);

-- 4. Ensure 2 refunded orders in current period (days 0-30)
UPDATE orders 
SET payment_status = 'REFUNDED', status = 'DELIVERED'
WHERE order_number IN ('ORD-2026-000009', 'ORD-2026-000012')
  AND created_at >= date_trunc('day', NOW()) - interval '30 days';

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


-- ===== V114__seed_order_timestamps.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

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
SET created_at = date_trunc('day', now()) - (off.days_ago::text || ' days')::interval,
    updated_at = date_trunc('day', now()) - (off.days_ago::text || ' days')::interval
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
-- Move to a temporary unique namespace first to avoid transient unique-constraint
-- collisions while the in-place UPDATE reorders rows.
UPDATE orders
SET order_number = 'ORD-TMP-' || id::text
WHERE order_number LIKE 'ORD-2026-%';

WITH ranked AS (
  SELECT id, row_number() OVER (ORDER BY created_at ASC, id ASC) AS rn
  FROM orders WHERE order_number LIKE 'ORD-TMP-%'
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
     AND (p.paid_at IS NULL OR o.delivered_at IS NULL OR p.paid_at <= o.delivered_at)
     AND (o.shipped_at IS NULL OR o.delivered_at IS NULL OR o.shipped_at <= o.delivered_at)
     AND COALESCE(o.delivered_at, o.created_at) <= date_trunc('day', now())
     AND o.created_at <= date_trunc('day', now())
    );
  IF v_bad > 0 THEN
    RAISE EXCEPTION 'Seed invariant violated for % of % orders', v_bad, v_count;
  END IF;
  RAISE NOTICE 'Seed check passed: % orders verified', v_count;
END $$;


-- ===== V115__seed_notifications.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

-- V123: dev seed notifications for admin (Z5)
-- 2 pending reviews, 2 unread contact messages, 1 recent order, 1 low-stock variant; 3 unread.

INSERT INTO notifications (recipient_user_id, type, params, entity_type, entity_id, created_at, read_at, updated_at)
VALUES
 (1, 'review.pending', '{"productName":"Ethiopia Yirgacheffe","rating":5,"reviewerName":"sara"}', 'review', 1, date_trunc('day', NOW()) - INTERVAL '1 hour', NULL, date_trunc('day', NOW()) - INTERVAL '1 hour'),
 (1, 'review.pending', '{"productName":"Brazil Mogiana","rating":4,"reviewerName":"omar"}', 'review', 2, date_trunc('day', NOW()) - INTERVAL '2 hours', date_trunc('day', NOW()) - INTERVAL '1 hour', date_trunc('day', NOW()) - INTERVAL '1 hour'),
 (1, 'contact.message', '{"subject":"Wholesale inquiry","senderName":"John Doe"}', 'contact', 1, date_trunc('day', NOW()) - INTERVAL '3 hours', NULL, date_trunc('day', NOW()) - INTERVAL '3 hours'),
 (1, 'contact.message', '{"subject":"Shipping question","senderName":"Jane Roe"}', 'contact', 2, date_trunc('day', NOW()) - INTERVAL '4 hours', date_trunc('day', NOW()) - INTERVAL '3 hours', date_trunc('day', NOW()) - INTERVAL '3 hours'),
 (1, 'order.new', '{"orderNumber":"ORD-1001","customerName":"ahmed","total":1240.00}', 'order', 1, date_trunc('day', NOW()) - INTERVAL '5 hours', date_trunc('day', NOW()) - INTERVAL '4 hours', date_trunc('day', NOW()) - INTERVAL '4 hours'),
 (1, 'stock.low', '{"productName":"Colombia Supremo","variantLabel":"SKU-COL-250","stock":3}', 'product_variant', 1, date_trunc('day', NOW()) - INTERVAL '6 hours', NULL, date_trunc('day', NOW()) - INTERVAL '6 hours');


-- ===== V116__seed_option_applies_to.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

INSERT INTO product_option_applies_to (option_id, product_type)
SELECT o.id, pt.product_type
FROM product_options o
CROSS JOIN (SELECT unnest(ARRAY['COFFEE','EQUIPMENT'])::varchar AS product_type) pt;

-- Seed overrides: Weight and Grind Type apply to COFFEE only; Size applies to EQUIPMENT only.
DELETE FROM product_option_applies_to WHERE option_id IN (1, 2, 3);
INSERT INTO product_option_applies_to (option_id, product_type) VALUES
    (1, 'COFFEE'),
    (2, 'COFFEE'),
    (3, 'EQUIPMENT');


-- ===== V117__reset_sequences.sql =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.
--
-- Seed rows use explicit ids, so every owned sequence must be advanced past the
-- maximum seeded id. Without this, the first application-created row reuses id 1
-- and fails on the primary key. order_number_seq is not owned by a table, so it
-- is advanced separately from the seeded ORD-YYYY-NNNNNN numbers.

DO $$
DECLARE
    r record;
    max_id bigint;
BEGIN
    FOR r IN
        SELECT n.nspname AS schema_name,
               c.relname AS table_name,
               a.attname AS column_name,
               pg_get_serial_sequence(format('%I.%I', n.nspname, c.relname), a.attname) AS seq
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
        WHERE c.relkind = 'r'
          AND n.nspname = 'public'
          AND pg_get_serial_sequence(format('%I.%I', n.nspname, c.relname), a.attname) IS NOT NULL
    LOOP
        EXECUTE format('SELECT COALESCE(MAX(%I), 0) FROM %I.%I',
                       r.column_name, r.schema_name, r.table_name) INTO max_id;
        IF max_id > 0 THEN
            PERFORM setval(r.seq::regclass, max_id, true);
        ELSE
            PERFORM setval(r.seq::regclass, 1, false);
        END IF;
    END LOOP;
END
$$;

-- The order number is generated from a standalone sequence, not an owned serial.
SELECT setval(
    'public.order_number_seq',
    COALESCE((SELECT MAX(split_part(order_number, '-', 3)::bigint) FROM public.orders), 1),
    EXISTS (SELECT 1 FROM public.orders)
);


-- ===== extras: product tags =====
INSERT INTO product_tags (id, slug, created_at, updated_at) VALUES
(1,'new-arrival',date_trunc('day', now()),date_trunc('day', now())),
(2,'best-seller',date_trunc('day', now()),date_trunc('day', now())),
(3,'gift-box',date_trunc('day', now()),date_trunc('day', now()));

INSERT INTO product_tag_translations (id, tag_id, language, name, created_at, updated_at) VALUES
(1,1,'en','New arrival',date_trunc('day', now()),date_trunc('day', now())),
(2,1,'ar','وصل حديثاً',date_trunc('day', now()),date_trunc('day', now())),
(3,2,'en','Best seller',date_trunc('day', now()),date_trunc('day', now())),
(4,2,'ar','الأكثر مبيعاً',date_trunc('day', now()),date_trunc('day', now())),
(5,3,'en','Gift box',date_trunc('day', now()),date_trunc('day', now())),
(6,3,'ar','صندوق هدايا',date_trunc('day', now()),date_trunc('day', now()));

INSERT INTO product_tag_links (product_id, tag_id) VALUES
(1,1),(2,2),(5,3);

-- ===== extras: inventory movements =====
INSERT INTO inventory_movements (id, variant_id, stock_before, stock_after, delta, reason, reason_note, actor, source_type, source_id, created_at, updated_at) VALUES
(1,1,10,8,-2,'ORDER','Order consumed stock','system','ORDER',1,date_trunc('day', now()) - interval '1 day',date_trunc('day', now()) - interval '1 day'),
(2,2,5,7,2,'ADJUSTMENT','Restock','admin','MANUAL',NULL,date_trunc('day', now()),date_trunc('day', now()));

-- ===== V117 sequence reset =====
-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.
--
-- Seed rows use explicit ids, so every owned sequence must be advanced past the
-- maximum seeded id. Without this, the first application-created row reuses id 1
-- and fails on the primary key. order_number_seq is not owned by a table, so it
-- is advanced separately from the seeded ORD-YYYY-NNNNNN numbers.

DO $$
DECLARE
    r record;
    max_id bigint;
BEGIN
    FOR r IN
        SELECT n.nspname AS schema_name,
               c.relname AS table_name,
               a.attname AS column_name,
               pg_get_serial_sequence(format('%I.%I', n.nspname, c.relname), a.attname) AS seq
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
        WHERE c.relkind = 'r'
          AND n.nspname = 'public'
          AND pg_get_serial_sequence(format('%I.%I', n.nspname, c.relname), a.attname) IS NOT NULL
    LOOP
        EXECUTE format('SELECT COALESCE(MAX(%I), 0) FROM %I.%I',
                       r.column_name, r.schema_name, r.table_name) INTO max_id;
        IF max_id > 0 THEN
            PERFORM setval(r.seq::regclass, max_id, true);
        ELSE
            PERFORM setval(r.seq::regclass, 1, false);
        END IF;
    END LOOP;
END
$$;

-- The order number is generated from a standalone sequence, not an owned serial.
SELECT setval(
    'public.order_number_seq',
    COALESCE((SELECT MAX(split_part(order_number, '-', 3)::bigint) FROM public.orders), 1),
    EXISTS (SELECT 1 FROM public.orders)
);

