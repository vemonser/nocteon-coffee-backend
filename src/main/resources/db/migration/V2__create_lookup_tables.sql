CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(100) UNIQUE NOT NULL,
    image_url VARCHAR(500) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE category_translations (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_category_language UNIQUE (category_id, language),
    CONSTRAINT uk_category_name_language UNIQUE (language, name)
);

CREATE TABLE origins (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(100) UNIQUE NOT NULL,
    code VARCHAR(10) NOT NULL,
    image_url VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE origin_translations (
    id BIGSERIAL PRIMARY KEY,
    origin_id BIGINT NOT NULL REFERENCES origins(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_origin_language UNIQUE (origin_id, language),
    CONSTRAINT uk_origin_name_language UNIQUE (language, name)
);

CREATE TABLE farms (
    id BIGSERIAL PRIMARY KEY,
    origin_id BIGINT NOT NULL REFERENCES origins(id),
    slug VARCHAR(100) UNIQUE NOT NULL,
    image_url VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE farm_translations (
    id BIGSERIAL PRIMARY KEY,
    farm_id BIGINT NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    country VARCHAR(100) NULL,
    description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_farm_language UNIQUE (farm_id, language),
    CONSTRAINT uk_farm_name_language UNIQUE (language, name)
);

CREATE TABLE roast_profiles (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(50) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE roast_profile_translations (
    id BIGSERIAL PRIMARY KEY,
    roast_profile_id BIGINT NOT NULL REFERENCES roast_profiles(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_roast_profile_language UNIQUE (roast_profile_id, language)
);

CREATE TABLE processing_methods (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(50) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE processing_method_translations (
    id BIGSERIAL PRIMARY KEY,
    processing_method_id BIGINT NOT NULL REFERENCES processing_methods(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_processing_method_language UNIQUE (processing_method_id, language)
);

CREATE TABLE coffee_varieties (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(50) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE coffee_variety_translations (
    id BIGSERIAL PRIMARY KEY,
    coffee_variety_id BIGINT NOT NULL REFERENCES coffee_varieties(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_coffee_variety_language UNIQUE (coffee_variety_id, language)
);

CREATE TABLE tasting_notes (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(50) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE tasting_note_translations (
    id BIGSERIAL PRIMARY KEY,
    tasting_note_id BIGINT NOT NULL REFERENCES tasting_notes(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_tasting_note_language UNIQUE (tasting_note_id, language)
);

CREATE TABLE brewing_methods (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(50) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE brewing_method_translations (
    id BIGSERIAL PRIMARY KEY,
    brewing_method_id BIGINT NOT NULL REFERENCES brewing_methods(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_brewing_method_language UNIQUE (brewing_method_id, language)
);

CREATE TABLE pairings (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(50) UNIQUE NOT NULL,
    image_url VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE pairing_translations (
    id BIGSERIAL PRIMARY KEY,
    pairing_id BIGINT NOT NULL REFERENCES pairings(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_pairing_language UNIQUE (pairing_id, language)
);