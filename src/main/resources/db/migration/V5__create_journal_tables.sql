CREATE TABLE journal_categories (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE journal_category_translations (
    id BIGSERIAL PRIMARY KEY,
    journal_category_id BIGINT NOT NULL REFERENCES journal_categories(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_journal_category_language UNIQUE (journal_category_id, language)
);

CREATE TABLE journal_posts (
    id BIGSERIAL PRIMARY KEY,
    journal_category_id BIGINT NOT NULL REFERENCES journal_categories(id),
    slug VARCHAR(150) UNIQUE NOT NULL,
    cover_image_url VARCHAR(500) NULL,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE journal_post_translations (
    id BIGSERIAL PRIMARY KEY,
    journal_post_id BIGINT NOT NULL REFERENCES journal_posts(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    title VARCHAR(200) NOT NULL,
    excerpt TEXT NULL,
    content TEXT NOT NULL,
    meta_title VARCHAR(200) NULL,
    meta_description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_journal_post_language UNIQUE (journal_post_id, language)
);

CREATE TABLE product_journal_posts (
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    journal_post_id BIGINT NOT NULL REFERENCES journal_posts(id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, journal_post_id)
);