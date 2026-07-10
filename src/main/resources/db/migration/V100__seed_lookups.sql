-- =========================================================
-- Categories
-- =========================================================

INSERT INTO categories (slug) VALUES
('coffee'),
('equipment'),
('gift-cards');

INSERT INTO category_translations (category_id, language, name, description) VALUES
(1,'en','Coffee','Specialty coffee beans'),
(1,'ar','القهوة','حبوب قهوة مختصة'),

(2,'en','Equipment','Coffee brewing equipment'),
(2,'ar','معدات التحضير','معدات تحضير القهوة'),

(3,'en','Gift Cards','Gift cards'),
(3,'ar','بطاقات الهدايا','بطاقات الهدايا');

-- =========================================================
-- Origins
-- =========================================================

INSERT INTO origins (slug, code) VALUES
('ethiopia','ET'),
('colombia','CO'),
('brazil','BR'),
('kenya','KE'),
('panama','PA');

INSERT INTO origin_translations (origin_id, language, name, description) VALUES
(1,'en','Ethiopia','Origin'),
(1,'ar','إثيوبيا','منشأ'),

(2,'en','Colombia','Origin'),
(2,'ar','كولومبيا','منشأ'),

(3,'en','Brazil','Origin'),
(3,'ar','البرازيل','منشأ'),

(4,'en','Kenya','Origin'),
(4,'ar','كينيا','منشأ'),

(5,'en','Panama','Origin'),
(5,'ar','بنما','منشأ');

-- =========================================================
-- Farms
-- =========================================================

INSERT INTO farms (origin_id, slug) VALUES
(1,'yirgacheffe'),
(1,'guji'),
(2,'huila'),
(3,'mogiana'),
(4,'nyeri');

INSERT INTO farm_translations
(farm_id, language, name, country, description)
VALUES
(1,'en','Yirgacheffe Farm','Ethiopia','Demo Farm'),
(1,'ar','مزرعة يرقاشيف','إثيوبيا','مزرعة تجريبية'),

(2,'en','Guji Farm','Ethiopia','Demo Farm'),
(2,'ar','مزرعة قوجي','إثيوبيا','مزرعة تجريبية'),

(3,'en','Huila Farm','Colombia','Demo Farm'),
(3,'ar','مزرعة هويلا','كولومبيا','مزرعة تجريبية'),

(4,'en','Mogiana Farm','Brazil','Demo Farm'),
(4,'ar','مزرعة موجيانا','البرازيل','مزرعة تجريبية'),

(5,'en','Nyeri Farm','Kenya','Demo Farm'),
(5,'ar','مزرعة نييري','كينيا','مزرعة تجريبية');

-- =========================================================
-- Roast Levels
-- =========================================================

INSERT INTO roast_levels(slug,color) VALUES
('light','#E8C39E'),
('medium-light','#C78652'),
('medium','#9A5A2D'),
('medium-dark','#63341B'),
('dark','#2B1810');

INSERT INTO roast_level_translations
(roast_level_id,language,name,description)
VALUES
(1,'en','Light',''),
(1,'ar','فاتح',''),

(2,'en','Medium Light',''),
(2,'ar','فاتح متوسط',''),

(3,'en','Medium',''),
(3,'ar','متوسط',''),

(4,'en','Medium Dark',''),
(4,'ar','متوسط غامق',''),

(5,'en','Dark',''),
(5,'ar','غامق','');

-- =========================================================
-- Processing Methods
-- =========================================================

INSERT INTO processing_methods(slug) VALUES
('washed'),
('natural'),
('honey'),
('anaerobic'),
('wet-hulled');

INSERT INTO processing_method_translations
(processing_method_id,language,name,description)
VALUES
(1,'en','Washed',''),
(1,'ar','مغسولة',''),

(2,'en','Natural',''),
(2,'ar','مجففة',''),

(3,'en','Honey',''),
(3,'ar','هاني',''),

(4,'en','Anaerobic',''),
(4,'ar','لاهوائية',''),

(5,'en','Wet Hulled',''),
(5,'ar','تقشير رطب','');

-- =========================================================
-- Coffee Varieties
-- =========================================================

INSERT INTO coffee_varieties(slug) VALUES
('gesha'),
('bourbon'),
('typica'),
('caturra'),
('sl28');

INSERT INTO coffee_variety_translations
(coffee_variety_id,language,name,description)
VALUES
(1,'en','Gesha',''),
(1,'ar','جيشا',''),

(2,'en','Bourbon',''),
(2,'ar','بوربون',''),

(3,'en','Typica',''),
(3,'ar','تيبيكا',''),

(4,'en','Caturra',''),
(4,'ar','كاتورا',''),

(5,'en','SL28',''),
(5,'ar','SL28','');

-- =========================================================
-- Tasting Notes
-- =========================================================

INSERT INTO tasting_notes(slug) VALUES
('chocolate'),
('caramel'),
('berry'),
('citrus'),
('jasmine'),
('honey'),
('nuts'),
('apple');

INSERT INTO tasting_note_translations
(tasting_note_id,language,name)
VALUES
(1,'en','Chocolate'),
(1,'ar','شوكولاتة'),

(2,'en','Caramel'),
(2,'ar','كراميل'),

(3,'en','Berry'),
(3,'ar','توت'),

(4,'en','Citrus'),
(4,'ar','حمضيات'),

(5,'en','Jasmine'),
(5,'ar','ياسمين'),

(6,'en','Honey'),
(6,'ar','عسل'),

(7,'en','Nuts'),
(7,'ar','مكسرات'),

(8,'en','Apple'),
(8,'ar','تفاح');

-- =========================================================
-- Brewing Methods
-- =========================================================

INSERT INTO brewing_methods(slug) VALUES
('v60'),
('espresso'),
('chemex'),
('aeropress'),
('french-press'),
('cold-brew');

INSERT INTO brewing_method_translations
(brewing_method_id,language,name,description)
VALUES
(1,'en','V60',''),
(1,'ar','V60',''),

(2,'en','Espresso',''),
(2,'ar','إسبريسو',''),

(3,'en','Chemex',''),
(3,'ar','كيميكس',''),

(4,'en','Aeropress',''),
(4,'ar','إيروبرس',''),

(5,'en','French Press',''),
(5,'ar','فرنش برس',''),

(6,'en','Cold Brew',''),
(6,'ar','كولد برو','');

-- =========================================================
-- Pairings
-- =========================================================

INSERT INTO pairings(slug) VALUES
('croissant'),
('cookies'),
('cheesecake'),
('dark-chocolate'),
('dates');

INSERT INTO pairing_translations
(pairing_id,language,name,description)
VALUES
(1,'en','Croissant',''),
(1,'ar','كرواسون',''),

(2,'en','Cookies',''),
(2,'ar','كوكيز',''),

(3,'en','Cheesecake',''),
(3,'ar','تشيز كيك',''),

(4,'en','Dark Chocolate',''),
(4,'ar','شوكولاتة داكنة',''),

(5,'en','Dates',''),
(5,'ar','تمر','');

-- =========================================================
-- Journal Categories
-- =========================================================

INSERT INTO journal_categories(slug) VALUES
('brewing-guides'),
('coffee-origins'),
('coffee-culture');

INSERT INTO journal_category_translations
(journal_category_id,language,name)
VALUES
(1,'en','Brewing Guides'),
(1,'ar','دليل التحضير'),

(2,'en','Coffee Origins'),
(2,'ar','أصول القهوة'),

(3,'en','Coffee Culture'),
(3,'ar','ثقافة القهوة');