-- Product SEO, per language, plus a product-level social image.
--
-- The storefront's product page had no meta title/description: every product
-- shared the same, generic preview. Both belong to a language (an Arabic page
-- needs an Arabic title), so they live on product_translations next to
-- name/description.
--
-- og_image_url is product-level on purpose: the social card is one image, not
-- one per language. NULL means "use the primary media", which the response
-- mapper resolves; seeding it with the current primary URL would freeze the
-- card and stop following a cover change.

ALTER TABLE public.product_translations ADD COLUMN IF NOT EXISTS meta_title character varying(200);

ALTER TABLE public.product_translations ADD COLUMN IF NOT EXISTS meta_description character varying(500);

ALTER TABLE public.products ADD COLUMN IF NOT EXISTS og_image_url text;
