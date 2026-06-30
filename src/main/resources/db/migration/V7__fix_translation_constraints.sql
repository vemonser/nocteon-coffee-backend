ALTER TABLE category_translations
    DROP CONSTRAINT IF EXISTS uk_category_name_language;

ALTER TABLE origin_translations
    DROP CONSTRAINT IF EXISTS uk_origin_name_language;

ALTER TABLE farm_translations
    DROP CONSTRAINT IF EXISTS uk_farm_name_language;