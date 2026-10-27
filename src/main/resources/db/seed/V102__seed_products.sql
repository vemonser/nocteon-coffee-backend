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
