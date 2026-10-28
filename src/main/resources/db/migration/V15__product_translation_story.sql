-- The product story belongs to a language, not to the product.
--
-- `coffee_details.story` was a single column, so an Arabic storefront showed
-- the English story (or nothing). The story now lives on product_translations
-- next to name/description; existing text is copied into every translation of
-- that product so nothing disappears. The old column stays as a read fallback
-- until every product has been re-saved.

ALTER TABLE public.product_translations ADD COLUMN IF NOT EXISTS story TEXT;

UPDATE public.product_translations pt
SET story = cd.story
FROM public.coffee_details cd
WHERE cd.product_id = pt.product_id
  AND pt.story IS NULL
  AND cd.story IS NOT NULL;
