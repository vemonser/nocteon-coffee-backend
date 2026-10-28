-- A variant can show its own photo. A 250 g bag and a 1 kg bag are the same
-- coffee in different packaging, and the gallery could not say so.
--
-- product-level media stays the fallback: NULL means "use the product gallery",
-- so an admin who never uploads a variant photo keeps today's behaviour and a
-- later cover change still reaches every variant.

ALTER TABLE public.product_variants ADD COLUMN IF NOT EXISTS image_url text;
