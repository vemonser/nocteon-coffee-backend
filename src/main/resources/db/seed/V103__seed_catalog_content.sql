-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.

-- =========================================================
-- Catalog content differentiation
-- =========================================================
-- WHY A FORWARD MIGRATION AND NOT AN EDIT TO V102
-- V102 is committed on main and already applied in every
-- developer and deployed database. Editing it changes its
-- checksum and Flyway refuses to start ("Validate failed:
-- migration checksum mismatch"). Seed data corrections must
-- therefore always be a new versioned file.
--
-- SCOPE: THE FOUR COFFEES ONLY
-- Products 5-7 (Hario V60, Chemex, AeroPress) also carry V102
-- placeholder copy -- "Demo equipment" / "معدات تحضير" /
-- "وصف" -- and are left untouched here. They are equipment
-- rather than coffee, so their placeholder text does not
-- compete with the coffees for a flavour-driven query, which
-- was the actual defect below.
--
-- THE PROBLEM
-- V102 gave all four coffees the same three tasting notes
-- (chocolate, caramel, berry), the same two brewing methods
-- with identical scores (V60 95 / Chemex 90), and the same
-- placeholder copy in both languages. Origin, roast, process
-- and variety were already distinct, so the documents were
-- not byte-identical -- but they were close enough that
-- retrieval could not tell them apart.
--
-- Consequence, measured against live Gemini embeddings of the
-- real indexed text before this migration: "ethiopian coffee"
-- scored Ethiopia 0.772 vs Colombia 0.680 (a real, usable
-- gap) while "chocolate notes" scored Ethiopia 0.7153 vs
-- Colombia 0.7151 -- a gap of 0.0002, which is noise.
-- Semantic search cannot discriminate documents whose
-- descriptive text is identical; the fix is content, not
-- ranking code.
--
-- AFTER THIS MIGRATION, measured the same way: "chocolate
-- notes" 0.672 vs 0.648 (gap 0.024, a 120x improvement) and
-- the four coffees each win their own probe in both English
-- and Arabic.
--
-- HOW TARGETS ARE ADDRESSED
-- Rows are selected by SLUG via products.slug, never by a
-- literal product_id. V102 happens to assign ids 1-4 in slug
-- order, but that is positional coupling to a file frozen on
-- main: a future migration inserting a product ahead of them
-- would make literal ids rewrite the wrong rows silently.
--
-- HOW OPERATOR EDITS ARE PROTECTED
-- Every statement is guarded so it only replaces content that
-- still matches the V102 placeholder it is correcting. A
-- database where someone has since written real copy through
-- the dashboard keeps that copy: this migration becomes a
-- partial no-op rather than destroying reviewed content. The
-- file's own content-review requirement (below) is only
-- honest because of these guards.
--
-- Tasting note ids: 1 chocolate, 2 caramel, 3 berry, 4 citrus,
--                   5 jasmine, 6 honey, 7 nuts, 8 apple
-- Brewing method ids: 1 v60, 2 espresso, 3 chemex,
--                     4 aeropress, 5 french-press, 6 cold-brew
--
-- Each profile below follows the roast / process / variety
-- V102 already assigned to that product, so the copy and the
-- structured attributes agree with each other:
--   Ethiopia  light    / washed   / gesha   -> floral, citrus
--   Colombia  medium   / natural  / bourbon -> caramel, nuts
--   Kenya     med-light/ honey    / sl28    -> berry, citrus
--   Brazil    med-dark / washed   / typica  -> chocolate, nuts
--
-- short_description is the field that matters for search:
-- ProductDocumentFactory embeds it for every translation, so
-- the Arabic copy is what Arabic queries match against. The
-- long description is not embedded; it is stored for display
-- and chatbot context.
--
-- CONTENT REVIEW REQUIRED: these are real product copy written
-- to replace placeholders, not authoritative commercial data.
-- A product owner should confirm the tasting profiles before
-- this reaches production. The guards below are what stop an
-- unreviewed profile from overwriting content that HAS been
-- reviewed.
-- =========================================================

-- =========================================================
-- Product copy (EN + AR)
-- =========================================================

-- 1. Ethiopia Yirgacheffe - light, washed gesha
UPDATE product_translations SET
    short_description = 'Floral light roast with jasmine and citrus',
    description = 'A washed Gesha from the Yirgacheffe region of Ethiopia. Delicate and tea-like, with a bright citrus acidity and a soft jasmine finish. Best brewed as a pour-over to keep its florals intact.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'ethiopia-yirgacheffe')
  AND language = 'en'
  AND short_description = 'Premium specialty coffee';

UPDATE product_translations SET
    short_description = 'قهوة فاتحة زهرية بنكهات الياسمين والحمضيات',
    description = 'قهوة جيسا معالجة بالغسل من منطقة يرقاشيف بإثيوبيا. رقيقة وناعمة، بحمضية حمضيات مشرقة ونهاية ياسمين. الأفضل تحضيرها بالتنقيط للحفاظ على نكهاتها الزهرية.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'ethiopia-yirgacheffe')
  AND language = 'ar'
  AND short_description = 'قهوة مختصة';

-- 2. Colombia Huila - medium, natural bourbon
UPDATE product_translations SET
    short_description = 'Caramel sweetness with toasted nuts',
    description = 'A naturally processed Bourbon from Huila, Colombia. Round and sweet, with caramel, toasted nuts and a gentle apple acidity. Forgiving across brew methods and excellent as espresso.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'colombia-huila')
  AND language = 'en'
  AND short_description = 'Premium specialty coffee';

UPDATE product_translations SET
    short_description = 'قهوة بحلاوة الكراميل والمكسرات المحمصة',
    description = 'قهوة بوربون معالجة بالطريقة الطبيعية من هويلا، كولومبيا. مستديرة وحلوة، بنكهات الكراميل والمكسرات المحمصة وحمضية تفاح خفيفة. مناسبة لكل طرق التحضير وممتازة للإسبريسو.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'colombia-huila')
  AND language = 'ar'
  AND short_description = 'قهوة مختصة';

-- 3. Kenya Nyeri - medium-light, honey processed SL28
UPDATE product_translations SET
    short_description = 'Juicy berry and grapefruit, honey processed',
    description = 'A honey-processed SL28 from Nyeri, Kenya. Juicy and vivid, with blackcurrant berry, grapefruit and a honeyed finish. A benchmark Kenyan lot for anyone chasing bright acidity.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'kenya-nyeri')
  AND language = 'en'
  AND short_description = 'Premium specialty coffee';

UPDATE product_translations SET
    short_description = 'قهوة فواكه طرية وجريب فروت معالجة بالعسل',
    description = 'قهوة كينية معالجة بالعسل من منطقة نييري. طرية ومشرقة، بنكهة الكشمش والجريب فروت ونهاية عسلية. من أجود أنواع القهوة الكينية لمن يبحث عن حمضية مشرقة.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'kenya-nyeri')
  AND language = 'ar'
  AND short_description = 'قهوة مختصة';

-- 4. Brazil Mogiana - medium-dark, washed typica
UPDATE product_translations SET
    short_description = 'Deep chocolate and hazelnut, low acidity',
    description = 'A washed Typica from the Mogiana region of Brazil. Full-bodied and low in acidity, with deep chocolate and hazelnut. The crowd-pleaser, and the easiest espresso base for a rich shot.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'brazil-mogiana')
  AND language = 'en'
  AND short_description = 'Premium specialty coffee';

UPDATE product_translations SET
    short_description = 'قهوة شوكولاتة عميقة وبندق بحمضية منخفضة',
    description = 'قهوة تيبيكا معالجة بالغسل من منطقة موجيانا بالبرازيل. ذات جسم قوي وحمضية منخفضة، بشوكولاتة عميقة ونكهة بندق. الأكثر طلباً وأسهل أساس للإسبريسو الغني.'
WHERE product_id IN (SELECT id FROM products WHERE slug = 'brazil-mogiana')
  AND language = 'ar'
  AND short_description = 'قهوة مختصة';

-- =========================================================
-- Tasting notes - the field with the largest retrieval weight
-- =========================================================
-- Rewritten wholesale rather than patched, because the defect
-- is that all four coffees carried the identical set: leaving
-- any of (1, 2, 3) in place would keep two of them
-- indistinguishable.
--
-- DELETE runs first and is guarded on the placeholder set
-- itself, so the two statements below compose into a clean swap:
-- the placeholder rows are removed only from products whose notes
-- are EXACTLY {1,2,3}, and the new rows are then inserted only for
-- those same products (the guard is re-evaluated, and it no longer
-- holds for a product that was just cleared). A product whose
-- flavour profile someone curated through the dashboard therefore
-- keeps it, and ends up with exactly what it had.
DELETE FROM product_tasting_notes x
WHERE x.product_id IN (SELECT id FROM products
                       WHERE slug IN ('ethiopia-yirgacheffe', 'colombia-huila',
                                      'kenya-nyeri', 'brazil-mogiana'))
  AND EXISTS (
    SELECT 1
    FROM product_tasting_notes y
    WHERE y.product_id = x.product_id
    GROUP BY y.product_id
    HAVING array_agg(y.tasting_note_id ORDER BY y.tasting_note_id) = ARRAY[1, 2, 3]::bigint[]
  );

INSERT INTO product_tasting_notes (product_id, tasting_note_id)
SELECT p.id, n.note_id
FROM products p
CROSS JOIN (VALUES
    ('ethiopia-yirgacheffe', 4), ('ethiopia-yirgacheffe', 5), ('ethiopia-yirgacheffe', 6),
    ('colombia-huila',      1), ('colombia-huila',      7), ('colombia-huila',      8),
    ('kenya-nyeri',         3), ('kenya-nyeri',         4), ('kenya-nyeri',         6),
    ('brazil-mogiana',      1), ('brazil-mogiana',      2), ('brazil-mogiana',      7)
) AS n(slug, note_id)
-- Without this join condition every product receives every product's notes.
WHERE p.slug = n.slug
  AND EXISTS (
    -- The product was just cleared of its placeholder set, i.e. it now has
    -- no tasting notes at all. Only such a product receives the new profile.
    SELECT 1
    FROM product_tasting_notes y
    WHERE y.product_id = p.id
    HAVING count(*) = 0
)
ON CONFLICT (product_id, tasting_note_id) DO NOTHING;

-- =========================================================
-- Brewing method fit - V102 gave every coffee V60 95 / Chemex 90
-- =========================================================
-- Replaced because a brewing question ("best coffee for
-- espresso") can only be answered if the espresso scores
-- differ, and V60 was the single strongest signal in V102 for
-- every product including the chocolatey Brazilian. Guarded on
-- the placeholder pair {V60 95, Chemex 90} for the same reason.
DELETE FROM product_brewing_methods b
WHERE b.product_id IN (SELECT id FROM products
                       WHERE slug IN ('ethiopia-yirgacheffe', 'colombia-huila',
                                      'kenya-nyeri', 'brazil-mogiana'))
  AND EXISTS (
    SELECT 1
    FROM product_brewing_methods x
    WHERE x.product_id = b.product_id
    GROUP BY x.product_id
    HAVING count(*) = 2
       AND array_agg(x.brewing_method_id ORDER BY x.brewing_method_id) = ARRAY[1, 3]::bigint[]
       AND min(x.score) = 90
       AND max(x.score) = 95
  );

INSERT INTO product_brewing_methods (product_id, brewing_method_id, score)
SELECT p.id, b.method_id, b.score
FROM products p
CROSS JOIN (VALUES
    ('ethiopia-yirgacheffe', 1, 97), ('ethiopia-yirgacheffe', 3, 94),
    ('colombia-huila',      2, 95), ('colombia-huila',      1, 87), ('colombia-huila', 5, 84),
    ('kenya-nyeri',         1, 94), ('kenya-nyeri',         4, 92), ('kenya-nyeri',      3, 86),
    ('brazil-mogiana',      2, 96), ('brazil-mogiana',      5, 92), ('brazil-mogiana',   4, 84)
) AS b(slug, method_id, score)
WHERE p.slug = b.slug
  AND EXISTS (
    -- Mirrors the tasting-note guard: the placeholder pair was just removed,
    -- so only a product left with no brew rows receives the new profile.
    SELECT 1
    FROM product_brewing_methods y
    WHERE y.product_id = p.id
    HAVING count(*) = 0
  );

-- =========================================================
-- RE-INDEXING IS A RUNTIME STEP, NOT A MIGRATION STEP
-- =========================================================
-- The corrected short_description and tasting notes change
-- buildText(), so ProductDocumentFactory's contentHash changes
-- and all four documents are reported stale by stats() on the
-- next call.
--
-- It does NOT self-heal, and this migration deliberately does not
-- attempt to:
--   * stats() only COUNTS stale rows; it never writes.
--   * Only indexAll() re-embeds. Call it explicitly:
--       POST /api/dashboard/ai/reindex      (requires the ai:manage permission)
--   * The nightly repair job is additionally gated on
--     AI_ENABLED && AI_SEARCH_ENABLED, both of which default to
--     false, so on a default configuration nothing re-embedded
--     the corrected copy until that endpoint was called.
--
-- Deliberately no embedding API key is required here: the
-- migration is pure SQL and runs offline at startup.
-- =========================================================
