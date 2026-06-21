CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(150) UNIQUE NOT NULL,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    origin_id BIGINT NULL REFERENCES origins(id),
    farm_id BIGINT NULL REFERENCES farms(id),
    roast_profile_id BIGINT NULL REFERENCES roast_profiles(id),
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
    processing_method_id BIGINT NULL REFERENCES processing_methods(id),
    coffee_variety_id BIGINT NULL REFERENCES coffee_varieties(id),
    altitude VARCHAR(50) NULL,
    harvest_year VARCHAR(10) NULL,
    story TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE product_variants (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku VARCHAR(100) UNIQUE NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    weight DECIMAL(8,2) NULL,
    grind_type VARCHAR(50) NULL,
    stock INT NOT NULL DEFAULT 0,
    discount DECIMAL(5,2) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

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
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    brewing_method_id BIGINT NOT NULL REFERENCES brewing_methods(id) ON DELETE CASCADE,
    score INT NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, brewing_method_id)
);

CREATE TABLE product_pairings (
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    pairing_id BIGINT NOT NULL REFERENCES pairings(id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, pairing_id)
);