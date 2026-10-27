-- Catalog, lookups and product graph
-- Final schema for this domain. Do not edit after the database is baselined;
-- add a new versioned migration instead.

CREATE TABLE public.brewing_method_translations (
    id bigint NOT NULL,
    brewing_method_id bigint NOT NULL,
    language character varying(10) NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.brewing_methods (
    id bigint NOT NULL,
    slug character varying(50) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.categories (
    id bigint NOT NULL,
    slug character varying(100) NOT NULL,
    image_url character varying(500),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.category_translations (
    id bigint NOT NULL,
    category_id bigint NOT NULL,
    language character varying(10) NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.coffee_details (
    id bigint NOT NULL,
    product_id bigint NOT NULL,
    roast_level_id bigint,
    processing_method_id bigint,
    coffee_variety_id bigint,
    altitude numeric(6,2),
    harvest_year smallint,
    story text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.coffee_varieties (
    id bigint NOT NULL,
    slug character varying(50) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.coffee_variety_translations (
    id bigint NOT NULL,
    coffee_variety_id bigint NOT NULL,
    language character varying(10) NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.farm_translations (
    id bigint NOT NULL,
    farm_id bigint NOT NULL,
    language character varying(10) NOT NULL,
    name character varying(100) NOT NULL,
    country character varying(100),
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.farms (
    id bigint NOT NULL,
    origin_id bigint NOT NULL,
    slug character varying(100) NOT NULL,
    image_url character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.origin_translations (
    id bigint NOT NULL,
    origin_id bigint NOT NULL,
    language character varying(10) NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.origins (
    id bigint NOT NULL,
    slug character varying(100) NOT NULL,
    code character varying(10) NOT NULL,
    image_url character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.pairing_translations (
    id bigint NOT NULL,
    pairing_id bigint NOT NULL,
    language character varying(10) NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.pairings (
    id bigint NOT NULL,
    slug character varying(50) NOT NULL,
    image_url character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.processing_method_translations (
    id bigint NOT NULL,
    processing_method_id bigint NOT NULL,
    language character varying(10) NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.processing_methods (
    id bigint NOT NULL,
    slug character varying(50) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.product_brewing_methods (
    id bigint NOT NULL,
    product_id bigint NOT NULL,
    brewing_method_id bigint NOT NULL,
    score integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.product_media (
    id bigint NOT NULL,
    product_id bigint NOT NULL,
    url character varying(500) NOT NULL,
    alt_text character varying(255),
    type character varying(20) DEFAULT 'IMAGE'::character varying NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.product_option_applies_to (
    option_id bigint NOT NULL,
    product_type character varying(20) NOT NULL
);

CREATE TABLE public.product_option_links (
    product_id bigint NOT NULL,
    option_id bigint NOT NULL
);

CREATE TABLE public.product_option_translations (
    id bigint NOT NULL,
    option_id bigint NOT NULL,
    language character varying(10) NOT NULL,
    name character varying(100) NOT NULL
);

CREATE TABLE public.product_option_value_translations (
    id bigint NOT NULL,
    option_value_id bigint NOT NULL,
    language character varying(10) NOT NULL,
    value character varying(150) NOT NULL
);

CREATE TABLE public.product_option_values (
    id bigint NOT NULL,
    option_id bigint NOT NULL,
    slug character varying(100) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    numeric_value numeric(10,2),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.product_options (
    id bigint NOT NULL,
    slug character varying(100) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.product_pairings (
    product_id bigint NOT NULL,
    pairing_id bigint NOT NULL
);

CREATE TABLE public.product_tasting_notes (
    product_id bigint NOT NULL,
    tasting_note_id bigint NOT NULL
);

CREATE TABLE public.product_translations (
    id bigint NOT NULL,
    product_id bigint NOT NULL,
    language character varying(10) NOT NULL,
    name character varying(200) NOT NULL,
    short_description text,
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.product_variants (
    id bigint NOT NULL,
    product_id bigint NOT NULL,
    sku character varying(100) NOT NULL,
    price numeric(10,2) NOT NULL,
    compare_at_price numeric(10,2),
    stock_quantity integer DEFAULT 0 NOT NULL,
    reserved_quantity integer DEFAULT 0 NOT NULL,
    cost_price numeric(10,2),
    reorder_threshold integer DEFAULT 5 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.products (
    id bigint NOT NULL,
    slug character varying(150) NOT NULL,
    category_id bigint NOT NULL,
    origin_id bigint,
    farm_id bigint,
    product_type character varying(20) NOT NULL,
    featured boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.roast_level_translations (
    id bigint NOT NULL,
    roast_level_id bigint NOT NULL,
    language character varying(10) NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.roast_levels (
    id bigint NOT NULL,
    slug character varying(50) NOT NULL,
    color character varying(10) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.tasting_note_translations (
    id bigint NOT NULL,
    tasting_note_id bigint NOT NULL,
    language character varying(10) NOT NULL,
    name character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.tasting_notes (
    id bigint NOT NULL,
    slug character varying(50) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.variant_option_values (
    variant_id bigint NOT NULL,
    option_value_id bigint NOT NULL
);

CREATE SEQUENCE public.brewing_method_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.brewing_methods_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.category_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.coffee_details_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.coffee_varieties_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.coffee_variety_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.farm_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.farms_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.origin_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.origins_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.pairing_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.pairings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.processing_method_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.processing_methods_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.product_brewing_methods_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.product_media_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.product_option_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.product_option_value_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.product_option_values_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.product_options_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.product_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.product_variants_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.roast_level_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.roast_levels_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.tasting_note_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.tasting_notes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.brewing_method_translations_id_seq OWNED BY public.brewing_method_translations.id;

ALTER SEQUENCE public.brewing_methods_id_seq OWNED BY public.brewing_methods.id;

ALTER SEQUENCE public.categories_id_seq OWNED BY public.categories.id;

ALTER SEQUENCE public.category_translations_id_seq OWNED BY public.category_translations.id;

ALTER SEQUENCE public.coffee_details_id_seq OWNED BY public.coffee_details.id;

ALTER SEQUENCE public.coffee_varieties_id_seq OWNED BY public.coffee_varieties.id;

ALTER SEQUENCE public.coffee_variety_translations_id_seq OWNED BY public.coffee_variety_translations.id;

ALTER SEQUENCE public.farm_translations_id_seq OWNED BY public.farm_translations.id;

ALTER SEQUENCE public.farms_id_seq OWNED BY public.farms.id;

ALTER SEQUENCE public.origin_translations_id_seq OWNED BY public.origin_translations.id;

ALTER SEQUENCE public.origins_id_seq OWNED BY public.origins.id;

ALTER SEQUENCE public.pairing_translations_id_seq OWNED BY public.pairing_translations.id;

ALTER SEQUENCE public.pairings_id_seq OWNED BY public.pairings.id;

ALTER SEQUENCE public.processing_method_translations_id_seq OWNED BY public.processing_method_translations.id;

ALTER SEQUENCE public.processing_methods_id_seq OWNED BY public.processing_methods.id;

ALTER SEQUENCE public.product_brewing_methods_id_seq OWNED BY public.product_brewing_methods.id;

ALTER SEQUENCE public.product_media_id_seq OWNED BY public.product_media.id;

ALTER SEQUENCE public.product_option_translations_id_seq OWNED BY public.product_option_translations.id;

ALTER SEQUENCE public.product_option_value_translations_id_seq OWNED BY public.product_option_value_translations.id;

ALTER SEQUENCE public.product_option_values_id_seq OWNED BY public.product_option_values.id;

ALTER SEQUENCE public.product_options_id_seq OWNED BY public.product_options.id;

ALTER SEQUENCE public.product_translations_id_seq OWNED BY public.product_translations.id;

ALTER SEQUENCE public.product_variants_id_seq OWNED BY public.product_variants.id;

ALTER SEQUENCE public.products_id_seq OWNED BY public.products.id;

ALTER SEQUENCE public.roast_level_translations_id_seq OWNED BY public.roast_level_translations.id;

ALTER SEQUENCE public.roast_levels_id_seq OWNED BY public.roast_levels.id;

ALTER SEQUENCE public.tasting_note_translations_id_seq OWNED BY public.tasting_note_translations.id;

ALTER SEQUENCE public.tasting_notes_id_seq OWNED BY public.tasting_notes.id;

ALTER TABLE ONLY public.brewing_method_translations ALTER COLUMN id SET DEFAULT nextval('public.brewing_method_translations_id_seq'::regclass);

ALTER TABLE ONLY public.brewing_methods ALTER COLUMN id SET DEFAULT nextval('public.brewing_methods_id_seq'::regclass);

ALTER TABLE ONLY public.categories ALTER COLUMN id SET DEFAULT nextval('public.categories_id_seq'::regclass);

ALTER TABLE ONLY public.category_translations ALTER COLUMN id SET DEFAULT nextval('public.category_translations_id_seq'::regclass);

ALTER TABLE ONLY public.coffee_details ALTER COLUMN id SET DEFAULT nextval('public.coffee_details_id_seq'::regclass);

ALTER TABLE ONLY public.coffee_varieties ALTER COLUMN id SET DEFAULT nextval('public.coffee_varieties_id_seq'::regclass);

ALTER TABLE ONLY public.coffee_variety_translations ALTER COLUMN id SET DEFAULT nextval('public.coffee_variety_translations_id_seq'::regclass);

ALTER TABLE ONLY public.farm_translations ALTER COLUMN id SET DEFAULT nextval('public.farm_translations_id_seq'::regclass);

ALTER TABLE ONLY public.farms ALTER COLUMN id SET DEFAULT nextval('public.farms_id_seq'::regclass);

ALTER TABLE ONLY public.origin_translations ALTER COLUMN id SET DEFAULT nextval('public.origin_translations_id_seq'::regclass);

ALTER TABLE ONLY public.origins ALTER COLUMN id SET DEFAULT nextval('public.origins_id_seq'::regclass);

ALTER TABLE ONLY public.pairing_translations ALTER COLUMN id SET DEFAULT nextval('public.pairing_translations_id_seq'::regclass);

ALTER TABLE ONLY public.pairings ALTER COLUMN id SET DEFAULT nextval('public.pairings_id_seq'::regclass);

ALTER TABLE ONLY public.processing_method_translations ALTER COLUMN id SET DEFAULT nextval('public.processing_method_translations_id_seq'::regclass);

ALTER TABLE ONLY public.processing_methods ALTER COLUMN id SET DEFAULT nextval('public.processing_methods_id_seq'::regclass);

ALTER TABLE ONLY public.product_brewing_methods ALTER COLUMN id SET DEFAULT nextval('public.product_brewing_methods_id_seq'::regclass);

ALTER TABLE ONLY public.product_media ALTER COLUMN id SET DEFAULT nextval('public.product_media_id_seq'::regclass);

ALTER TABLE ONLY public.product_option_translations ALTER COLUMN id SET DEFAULT nextval('public.product_option_translations_id_seq'::regclass);

ALTER TABLE ONLY public.product_option_value_translations ALTER COLUMN id SET DEFAULT nextval('public.product_option_value_translations_id_seq'::regclass);

ALTER TABLE ONLY public.product_option_values ALTER COLUMN id SET DEFAULT nextval('public.product_option_values_id_seq'::regclass);

ALTER TABLE ONLY public.product_options ALTER COLUMN id SET DEFAULT nextval('public.product_options_id_seq'::regclass);

ALTER TABLE ONLY public.product_translations ALTER COLUMN id SET DEFAULT nextval('public.product_translations_id_seq'::regclass);

ALTER TABLE ONLY public.product_variants ALTER COLUMN id SET DEFAULT nextval('public.product_variants_id_seq'::regclass);

ALTER TABLE ONLY public.products ALTER COLUMN id SET DEFAULT nextval('public.products_id_seq'::regclass);

ALTER TABLE ONLY public.roast_level_translations ALTER COLUMN id SET DEFAULT nextval('public.roast_level_translations_id_seq'::regclass);

ALTER TABLE ONLY public.roast_levels ALTER COLUMN id SET DEFAULT nextval('public.roast_levels_id_seq'::regclass);

ALTER TABLE ONLY public.tasting_note_translations ALTER COLUMN id SET DEFAULT nextval('public.tasting_note_translations_id_seq'::regclass);

ALTER TABLE ONLY public.tasting_notes ALTER COLUMN id SET DEFAULT nextval('public.tasting_notes_id_seq'::regclass);

ALTER TABLE ONLY public.brewing_method_translations
    ADD CONSTRAINT brewing_method_translations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.brewing_method_translations
    ADD CONSTRAINT uk_brewing_method_language UNIQUE (brewing_method_id, language);

ALTER TABLE ONLY public.brewing_methods
    ADD CONSTRAINT brewing_methods_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.brewing_methods
    ADD CONSTRAINT brewing_methods_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.category_translations
    ADD CONSTRAINT category_translations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.category_translations
    ADD CONSTRAINT uk_category_language UNIQUE (category_id, language);

ALTER TABLE ONLY public.coffee_details
    ADD CONSTRAINT coffee_details_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.coffee_details
    ADD CONSTRAINT coffee_details_product_id_key UNIQUE (product_id);

ALTER TABLE ONLY public.coffee_varieties
    ADD CONSTRAINT coffee_varieties_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.coffee_varieties
    ADD CONSTRAINT coffee_varieties_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.coffee_variety_translations
    ADD CONSTRAINT coffee_variety_translations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.coffee_variety_translations
    ADD CONSTRAINT uk_coffee_variety_language UNIQUE (coffee_variety_id, language);

ALTER TABLE ONLY public.farm_translations
    ADD CONSTRAINT farm_translations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.farm_translations
    ADD CONSTRAINT uk_farm_language UNIQUE (farm_id, language);

ALTER TABLE ONLY public.farms
    ADD CONSTRAINT farms_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.farms
    ADD CONSTRAINT farms_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.origin_translations
    ADD CONSTRAINT origin_translations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.origin_translations
    ADD CONSTRAINT uk_origin_language UNIQUE (origin_id, language);

ALTER TABLE ONLY public.origins
    ADD CONSTRAINT origins_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.origins
    ADD CONSTRAINT origins_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.pairing_translations
    ADD CONSTRAINT pairing_translations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.pairing_translations
    ADD CONSTRAINT uk_pairing_language UNIQUE (pairing_id, language);

ALTER TABLE ONLY public.pairings
    ADD CONSTRAINT pairings_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.pairings
    ADD CONSTRAINT pairings_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.processing_method_translations
    ADD CONSTRAINT processing_method_translations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.processing_method_translations
    ADD CONSTRAINT uk_processing_method_language UNIQUE (processing_method_id, language);

ALTER TABLE ONLY public.processing_methods
    ADD CONSTRAINT processing_methods_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.processing_methods
    ADD CONSTRAINT processing_methods_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.product_brewing_methods
    ADD CONSTRAINT product_brewing_methods_pkey PRIMARY KEY (product_id, brewing_method_id);

ALTER TABLE ONLY public.product_media
    ADD CONSTRAINT product_media_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_option_applies_to
    ADD CONSTRAINT product_option_applies_to_pkey PRIMARY KEY (option_id, product_type);

ALTER TABLE ONLY public.product_option_links
    ADD CONSTRAINT product_option_links_pkey PRIMARY KEY (product_id, option_id);

ALTER TABLE ONLY public.product_option_translations
    ADD CONSTRAINT product_option_translations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_option_translations
    ADD CONSTRAINT uk_option_language UNIQUE (option_id, language);

ALTER TABLE ONLY public.product_option_value_translations
    ADD CONSTRAINT product_option_value_translations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_option_value_translations
    ADD CONSTRAINT uk_option_value_language UNIQUE (option_value_id, language);

ALTER TABLE ONLY public.product_option_values
    ADD CONSTRAINT product_option_values_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_option_values
    ADD CONSTRAINT uk_option_value_slug UNIQUE (option_id, slug);

ALTER TABLE ONLY public.product_options
    ADD CONSTRAINT product_options_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_options
    ADD CONSTRAINT product_options_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.product_pairings
    ADD CONSTRAINT product_pairings_pkey PRIMARY KEY (product_id, pairing_id);

ALTER TABLE ONLY public.product_tasting_notes
    ADD CONSTRAINT product_tasting_notes_pkey PRIMARY KEY (product_id, tasting_note_id);

ALTER TABLE ONLY public.product_translations
    ADD CONSTRAINT product_translations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_translations
    ADD CONSTRAINT uk_product_language UNIQUE (product_id, language);

ALTER TABLE ONLY public.product_variants
    ADD CONSTRAINT product_variants_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_variants
    ADD CONSTRAINT product_variants_sku_key UNIQUE (sku);

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.roast_level_translations
    ADD CONSTRAINT roast_level_translations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.roast_level_translations
    ADD CONSTRAINT uk_roast_level_language UNIQUE (roast_level_id, language);

ALTER TABLE ONLY public.roast_levels
    ADD CONSTRAINT roast_levels_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.roast_levels
    ADD CONSTRAINT roast_levels_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.tasting_note_translations
    ADD CONSTRAINT tasting_note_translations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tasting_note_translations
    ADD CONSTRAINT uk_tasting_note_language UNIQUE (tasting_note_id, language);

ALTER TABLE ONLY public.tasting_notes
    ADD CONSTRAINT tasting_notes_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tasting_notes
    ADD CONSTRAINT tasting_notes_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.variant_option_values
    ADD CONSTRAINT variant_option_values_pkey PRIMARY KEY (variant_id, option_value_id);

CREATE INDEX idx_coffee_details_coffee_variety ON public.coffee_details USING btree (coffee_variety_id);

CREATE INDEX idx_coffee_details_processing_method ON public.coffee_details USING btree (processing_method_id);

CREATE INDEX idx_coffee_details_roast_level ON public.coffee_details USING btree (roast_level_id);

CREATE INDEX idx_option_values_option ON public.product_option_values USING btree (option_id);

CREATE INDEX idx_product_brewing_methods_product ON public.product_brewing_methods USING btree (product_id);

CREATE INDEX idx_product_media_product ON public.product_media USING btree (product_id);

CREATE INDEX idx_product_pairings_product ON public.product_pairings USING btree (product_id);

CREATE INDEX idx_product_tasting_notes_product ON public.product_tasting_notes USING btree (product_id);

CREATE INDEX idx_product_translation_name ON public.product_translations USING btree (name);

CREATE INDEX idx_product_variants_active ON public.product_variants USING btree (is_active) WHERE (deleted_at IS NULL);

CREATE INDEX idx_product_variants_product ON public.product_variants USING btree (product_id) WHERE (deleted_at IS NULL);

CREATE INDEX idx_product_variants_product_id ON public.product_variants USING btree (product_id);

CREATE INDEX idx_product_variants_sku ON public.product_variants USING btree (sku) WHERE (deleted_at IS NULL);

CREATE INDEX idx_products_active ON public.products USING btree (is_active) WHERE (deleted_at IS NULL);

CREATE INDEX idx_products_category ON public.products USING btree (category_id);

CREATE INDEX idx_products_farm ON public.products USING btree (farm_id);

CREATE INDEX idx_products_origin ON public.products USING btree (origin_id);

CREATE INDEX idx_products_product_type ON public.products USING btree (product_type);

CREATE INDEX idx_variant_option_values_value ON public.variant_option_values USING btree (option_value_id);

CREATE INDEX idx_variant_option_values_variant ON public.variant_option_values USING btree (variant_id);

CREATE UNIQUE INDEX uk_product_primary_media ON public.product_media USING btree (product_id) WHERE (is_primary = true);
