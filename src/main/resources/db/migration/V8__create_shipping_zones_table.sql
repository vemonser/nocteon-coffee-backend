CREATE TABLE shipping_zones (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    shipping_cost NUMERIC(10,2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL
);
CREATE TABLE shipping_zone_cities (
    shipping_zone_id BIGINT NOT NULL REFERENCES shipping_zones(id) ON DELETE CASCADE,
    city VARCHAR(100) NOT NULL,
    PRIMARY KEY (shipping_zone_id, city)
);

CREATE INDEX idx_shipping_zone_cities_city ON shipping_zone_cities(city);
