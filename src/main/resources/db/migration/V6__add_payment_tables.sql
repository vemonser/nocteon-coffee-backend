ALTER TABLE orders
    DROP COLUMN IF EXISTS payment_id,
    DROP COLUMN IF EXISTS payment_status;

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    provider VARCHAR(50) NOT NULL DEFAULT 'PAYMOB',
    provider_payment_id VARCHAR(255) NULL,
    provider_order_id VARCHAR(255) NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'EGP',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(50) NULL,
    failure_reason TEXT NULL,
    paid_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE orders
    ADD COLUMN payment_status VARCHAR(50) NOT NULL DEFAULT 'PENDING';