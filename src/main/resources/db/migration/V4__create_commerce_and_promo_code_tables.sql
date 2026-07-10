CREATE TABLE
    addresses (
        id BIGSERIAL PRIMARY KEY,
        user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        full_name VARCHAR(100) NOT NULL,
        phone VARCHAR(20) NOT NULL,
        street VARCHAR(255) NOT NULL,
        city VARCHAR(100) NOT NULL,
        state VARCHAR(100),
        country VARCHAR(100) NOT NULL,
        postal_code VARCHAR(20),
        is_default BOOLEAN NOT NULL DEFAULT FALSE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW ()
    );

CREATE TABLE
    carts (
        id BIGSERIAL PRIMARY KEY,
        user_id BIGINT NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
        last_reminded_at TIMESTAMPTZ,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW ()
    );

CREATE TABLE
    cart_items (
        id BIGSERIAL PRIMARY KEY,
        cart_id BIGINT NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
        product_variant_id BIGINT NOT NULL REFERENCES product_variants (id),
        quantity INT NOT NULL DEFAULT 1,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        CONSTRAINT uk_cart_variant UNIQUE (cart_id, product_variant_id)
    );

CREATE TABLE
    wishlists (
        id BIGSERIAL PRIMARY KEY,
        user_id BIGINT NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW ()
    );

CREATE TABLE
    wishlist_items (
        id BIGSERIAL PRIMARY KEY,
        wishlist_id BIGINT NOT NULL REFERENCES wishlists (id) ON DELETE CASCADE,
        product_id BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        CONSTRAINT uk_wishlist_product UNIQUE (wishlist_id, product_id)
    );

CREATE TABLE
    promo_codes (
        id BIGSERIAL PRIMARY KEY,
        code VARCHAR(50) NOT NULL UNIQUE,
        discount_type VARCHAR(20) NOT NULL,
        discount_value NUMERIC(10, 2),
        min_order_amount NUMERIC(10, 2),
        scope_type VARCHAR(20) NOT NULL,
        max_total_redemptions INT,
        max_redemptions_per_user INT NOT NULL DEFAULT 1,
        valid_from TIMESTAMPTZ NOT NULL,
        valid_until TIMESTAMPTZ NOT NULL,
        is_active BOOLEAN NOT NULL DEFAULT TRUE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        deleted_at TIMESTAMPTZ
    );

CREATE INDEX idx_promo_codes_code ON promo_codes (code);

CREATE TABLE
    promo_code_categories (
        promo_code_id BIGINT NOT NULL REFERENCES promo_codes (id) ON DELETE CASCADE,
        category_id BIGINT NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
        PRIMARY KEY (promo_code_id, category_id)
    );

CREATE TABLE
    orders (
        id BIGSERIAL PRIMARY KEY,
        user_id BIGINT NOT NULL REFERENCES users (id),
        address_id BIGINT NOT NULL REFERENCES addresses (id),
        status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
        payment_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
        payment_method VARCHAR(50) NOT NULL DEFAULT 'ONLINE',
        shipping_cost NUMERIC(10, 2) NOT NULL DEFAULT 0,
        promo_code_id BIGINT REFERENCES promo_codes (id) ON DELETE SET NULL,
        total_amount DECIMAL(10, 2) NOT NULL,
        discount_amount NUMERIC(10, 2),
        notes TEXT,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        deleted_at TIMESTAMPTZ
    );

CREATE TABLE
    order_items (
        id BIGSERIAL PRIMARY KEY,
        order_id BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
        product_variant_id BIGINT NOT NULL REFERENCES product_variants (id),
        promo_code_id BIGINT REFERENCES promo_codes (id) ON DELETE SET NULL,
        discount_amount NUMERIC(10, 2),
        quantity INT NOT NULL,
        unit_price DECIMAL(10, 2) NOT NULL,
        total_price DECIMAL(10, 2) NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW ()
    );

CREATE TABLE
    promo_code_redemptions (
        id BIGSERIAL PRIMARY KEY,
        promo_code_id BIGINT NOT NULL REFERENCES promo_codes (id) ON DELETE CASCADE,
        user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        order_id BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
        discount_amount NUMERIC(10, 2) NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        CONSTRAINT uk_promo_redemption_order UNIQUE (order_id)
    );

CREATE INDEX idx_promo_redemptions_promo_code ON promo_code_redemptions (promo_code_id);

CREATE INDEX idx_promo_redemptions_user ON promo_code_redemptions (user_id);

CREATE INDEX idx_promo_redemptions_order ON promo_code_redemptions (order_id);

CREATE INDEX idx_promo_redemptions_user_promo ON promo_code_redemptions (user_id, promo_code_id);

CREATE TABLE
    payments (
        id BIGSERIAL PRIMARY KEY,
        order_id BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
        provider VARCHAR(50) NOT NULL DEFAULT 'PAYMOB',
        provider_payment_id VARCHAR(255),
        provider_order_id VARCHAR(255),
        amount DECIMAL(10, 2) NOT NULL,
        currency VARCHAR(10) NOT NULL DEFAULT 'EGP',
        status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
        payment_method VARCHAR(50),
        failure_reason TEXT,
        attempt_number INTEGER NOT NULL DEFAULT 1,
        paid_at TIMESTAMPTZ,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW ()
    );

CREATE TABLE
    reviews (
        id BIGSERIAL PRIMARY KEY,
        product_id BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
        user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
        rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
        comment TEXT,
        is_verified BOOLEAN NOT NULL DEFAULT FALSE,
        is_approved BOOLEAN NOT NULL DEFAULT FALSE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        deleted_at TIMESTAMPTZ,
        CONSTRAINT uk_user_product_review UNIQUE (user_id, product_id)
    );

CREATE INDEX idx_reviews_is_approved ON reviews (is_approved)
WHERE
    deleted_at IS NULL;