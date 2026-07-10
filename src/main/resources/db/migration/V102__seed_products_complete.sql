BEGIN;

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
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        weight_grams,
        grind_type,
        stock_quantity,
        is_active
    )
VALUES
    (
        1,
        'SKU-1-1',
        460,
        510,
        250,
        'WHOLE_BEAN',
        50,
        TRUE
    );

INSERT INTO
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        weight_grams,
        grind_type,
        stock_quantity,
        is_active
    )
VALUES
    (1, 'SKU-1-2', 500, 550, 250, 'ESPRESSO', 50, TRUE);

INSERT INTO
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        weight_grams,
        grind_type,
        stock_quantity,
        is_active
    )
VALUES
    (1, 'SKU-1-3', 540, 590, 500, 'V60', 50, TRUE);

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
    );

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
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=12',
        'Ethiopia Yirgacheffe',
        'IMAGE',
        2,
        FALSE
    );

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
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        weight_grams,
        grind_type,
        stock_quantity,
        is_active
    )
VALUES
    (
        2,
        'SKU-2-1',
        480,
        530,
        250,
        'WHOLE_BEAN',
        50,
        TRUE
    );

INSERT INTO
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        weight_grams,
        grind_type,
        stock_quantity,
        is_active
    )
VALUES
    (2, 'SKU-2-2', 520, 570, 250, 'ESPRESSO', 50, TRUE);

INSERT INTO
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        weight_grams,
        grind_type,
        stock_quantity,
        is_active
    )
VALUES
    (2, 'SKU-2-3', 560, 610, 500, 'V60', 50, TRUE);

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
    );

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
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=22',
        'Colombia Huila',
        'IMAGE',
        2,
        FALSE
    );

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
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        weight_grams,
        grind_type,
        stock_quantity,
        is_active
    )
VALUES
    (
        3,
        'SKU-3-1',
        500,
        550,
        250,
        'WHOLE_BEAN',
        50,
        TRUE
    );

INSERT INTO
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        weight_grams,
        grind_type,
        stock_quantity,
        is_active
    )
VALUES
    (3, 'SKU-3-2', 540, 590, 250, 'ESPRESSO', 50, TRUE);

INSERT INTO
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        weight_grams,
        grind_type,
        stock_quantity,
        is_active
    )
VALUES
    (3, 'SKU-3-3', 580, 630, 500, 'V60', 50, TRUE);

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
    );

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
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=32',
        'Kenya Nyeri',
        'IMAGE',
        2,
        FALSE
    );

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
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        weight_grams,
        grind_type,
        stock_quantity,
        is_active
    )
VALUES
    (
        4,
        'SKU-4-1',
        520,
        570,
        250,
        'WHOLE_BEAN',
        50,
        TRUE
    );

INSERT INTO
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        weight_grams,
        grind_type,
        stock_quantity,
        is_active
    )
VALUES
    (4, 'SKU-4-2', 560, 610, 250, 'ESPRESSO', 50, TRUE);

INSERT INTO
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        weight_grams,
        grind_type,
        stock_quantity,
        is_active
    )
VALUES
    (4, 'SKU-4-3', 600, 650, 500, 'V60', 50, TRUE);

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
    );

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
        'https://images.unsplash.com/photo-1447933601403-0c6688de566e?sig=42',
        'Brazil Mogiana',
        'IMAGE',
        2,
        FALSE
    );

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
    product_variants (
        product_id,
        sku,
        price,
        compare_at_price,
        weight_grams,
        grind_type,
        stock_quantity,
        is_active
    )
VALUES
    (5, 'EQ-5', 650, NULL, NULL, NULL, 20, TRUE);

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
        weight_grams,
        grind_type,
        stock_quantity,
        is_active
    )
VALUES
    (6, 'EQ-6', 650, NULL, NULL, NULL, 20, TRUE);

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
        weight_grams,
        grind_type,
        stock_quantity,
        is_active
    )
VALUES
    (7, 'EQ-7', 650, NULL, NULL, NULL, 20, TRUE);

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

COMMIT;