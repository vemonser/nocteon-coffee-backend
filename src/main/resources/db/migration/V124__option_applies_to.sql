-- C4: option applicability (appliesTo)
-- Rollback note: DROP TABLE product_option_applies_to; (entity will fall back? App requires table; revert code too.)

CREATE TABLE product_option_applies_to (
    option_id BIGINT NOT NULL REFERENCES product_options(id) ON DELETE CASCADE,
    product_type VARCHAR(20) NOT NULL,
    PRIMARY KEY (option_id, product_type)
);

-- Default: all existing options apply to all product types.
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
