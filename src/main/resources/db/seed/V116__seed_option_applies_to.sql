-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

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
