CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(150) UNIQUE NOT NULL,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    origin_id BIGINT NULL REFERENCES origins(id),
    farm_id BIGINT NULL REFERENCES farms(id),
    product_type VARCHAR(20) NOT NULL,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE product_translations (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    name VARCHAR(200) NOT NULL,
    short_description TEXT NULL,
    description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_product_language UNIQUE (product_id, language)
);

CREATE TABLE coffee_details (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE REFERENCES products(id) ON DELETE CASCADE,
    roast_level_id BIGINT NULL REFERENCES roast_levels(id),
    processing_method_id BIGINT NULL REFERENCES processing_methods(id),
    coffee_variety_id BIGINT NULL REFERENCES coffee_varieties(id),
    altitude DECIMAL(6,2) NULL,
    harvest_year SMALLINT NULL,
    story TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE product_variants (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL
        REFERENCES products(id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL UNIQUE,
    price DECIMAL(10,2) NOT NULL,
    compare_at_price DECIMAL(10,2),
    weight_grams DECIMAL(8,2),
    grind_type VARCHAR(50),
    stock_quantity INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_product_variants_product_id
    ON product_variants(product_id);

CREATE INDEX idx_product_variants_active
    ON product_variants(is_active)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_product_variants_sku
    ON product_variants(sku)
    WHERE deleted_at IS NULL;

CREATE TABLE product_media (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    url VARCHAR(500) NOT NULL,
    alt_text VARCHAR(255) NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'IMAGE',
    sort_order INT NOT NULL DEFAULT 0,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE product_tasting_notes (
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    tasting_note_id BIGINT NOT NULL REFERENCES tasting_notes(id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, tasting_note_id)
);

CREATE TABLE product_brewing_methods (
    id BIGSERIAL,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    brewing_method_id BIGINT NOT NULL REFERENCES brewing_methods(id) ON DELETE CASCADE,
    score INT NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, brewing_method_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE product_pairings (
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    pairing_id BIGINT NOT NULL REFERENCES pairings(id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, pairing_id)
);

CREATE TABLE store_settings (
    id BIGSERIAL PRIMARY KEY,
    free_shipping_threshold NUMERIC(10,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO store_settings (id, free_shipping_threshold) VALUES (1, 500.00);
-- =========================================================
-- Indexes
-- =========================================================

CREATE INDEX idx_products_category
ON products(category_id);

CREATE INDEX idx_products_origin
ON products(origin_id);

CREATE INDEX idx_products_farm
ON products(farm_id);

CREATE INDEX idx_products_product_type
ON products(product_type);

CREATE INDEX idx_products_active
ON products(is_active)
WHERE deleted_at IS NULL;

CREATE INDEX idx_product_translation_name
ON product_translations(name);

CREATE INDEX idx_product_variants_product
ON product_variants(product_id)
WHERE deleted_at IS NULL;

CREATE INDEX idx_product_media_product
ON product_media(product_id);

CREATE UNIQUE INDEX uk_product_primary_media
ON product_media(product_id)
WHERE is_primary = TRUE;

CREATE INDEX idx_coffee_details_roast_level
ON coffee_details(roast_level_id);

CREATE INDEX idx_coffee_details_processing_method
ON coffee_details(processing_method_id);

CREATE INDEX idx_coffee_details_coffee_variety
ON coffee_details(coffee_variety_id);

CREATE INDEX idx_product_brewing_methods_product
ON product_brewing_methods(product_id);

CREATE INDEX idx_product_tasting_notes_product
ON product_tasting_notes(product_id);

CREATE INDEX idx_product_pairings_product
ON product_pairings(product_id);