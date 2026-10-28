-- Merchandising labels that cut across the catalogue ("New arrivals", "Gift boxes").
--
-- A category answers "what is it"; a tag answers "why would I look for it". The
-- two cannot share a column, and a product needs several tags, so this is a
-- many-to-many with its own translations table (the shape journal categories
-- already use for the same reason: the display name is per language, the slug
-- is the stable identity that lists and filters share).
--
-- Links are kept when a product is trashed, and the delete guard counts them:
-- a tag whose only products are in the trash must not vanish, or restoring one
-- would leave it pointing at a deleted row.

CREATE TABLE public.product_tags (
    id bigint NOT NULL,
    slug character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.product_tag_translations (
    id bigint NOT NULL,
    tag_id bigint NOT NULL,
    language character varying(10) NOT NULL,
    name character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.product_tag_links (
    product_id bigint NOT NULL,
    tag_id bigint NOT NULL
);

CREATE SEQUENCE public.product_tags_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.product_tag_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.product_tags_id_seq OWNED BY public.product_tags.id;

ALTER SEQUENCE public.product_tag_translations_id_seq OWNED BY public.product_tag_translations.id;

ALTER TABLE ONLY public.product_tags ALTER COLUMN id SET DEFAULT nextval('public.product_tags_id_seq'::regclass);

ALTER TABLE ONLY public.product_tag_translations ALTER COLUMN id SET DEFAULT nextval('public.product_tag_translations_id_seq'::regclass);

ALTER TABLE ONLY public.product_tags
    ADD CONSTRAINT product_tags_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_tags
    ADD CONSTRAINT product_tags_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.product_tag_translations
    ADD CONSTRAINT product_tag_translations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_tag_links
    ADD CONSTRAINT product_tag_links_pkey PRIMARY KEY (product_id, tag_id);

ALTER TABLE ONLY public.product_tag_translations
    ADD CONSTRAINT product_tag_translations_tag_id_fkey FOREIGN KEY (tag_id) REFERENCES public.product_tags(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_tag_links
    ADD CONSTRAINT product_tag_links_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_tag_links
    ADD CONSTRAINT product_tag_links_tag_id_fkey FOREIGN KEY (tag_id) REFERENCES public.product_tags(id) ON DELETE CASCADE;

-- The delete guard counts products per tag, and the products list filters by
-- tag; both read this table from the tag side.
CREATE INDEX idx_product_tag_links_tag ON public.product_tag_links (tag_id);
