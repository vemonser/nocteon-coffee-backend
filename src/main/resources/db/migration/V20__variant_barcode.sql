-- FE-15: a variant can carry a barcode (GTIN / EAN / UPC).
--
-- Optional on purpose: there is no GTIN checksum at the API boundary, so the
-- column is a plain string and an admin who never fills it keeps today's
-- behaviour. Blank input is stored as NULL, never as '', so "no barcode" has
-- one representation and the export can tell it from a real value.
--
-- Uniqueness is a PARTIAL unique index. A plain UNIQUE constraint would accept
-- only one NULL row, which would stop the second variant that has no barcode —
-- exactly the common case. Skipping the NULLs is what makes the constraint
-- mean "unique when set".
--
-- The index is deliberately unfiltered on deleted_at: a trashed variant keeps
-- its row (order_items references it), so the value still occupies the column
-- and the API reports it as taken instead of failing on the index.

ALTER TABLE public.product_variants ADD COLUMN IF NOT EXISTS barcode character varying(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_variant_barcode ON public.product_variants (barcode)
    WHERE barcode IS NOT NULL;

-- The search and the products export both read product_variants by product_id;
-- that access path is already served by idx_variant_product (V3), so this
-- migration adds no second one.
