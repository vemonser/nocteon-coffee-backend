-- Journal/content tables
-- Final schema for this domain. Do not edit after the database is baselined;
-- add a new versioned migration instead.

CREATE TABLE public.journal_categories (
    id bigint NOT NULL,
    slug character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.journal_category_translations (
    id bigint NOT NULL,
    journal_category_id bigint NOT NULL,
    language character varying(10) NOT NULL,
    name character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.journal_post_tags (
    journal_post_id bigint NOT NULL,
    tag character varying(100) NOT NULL
);

CREATE TABLE public.journal_post_translations (
    id bigint NOT NULL,
    journal_post_id bigint NOT NULL,
    language character varying(10) NOT NULL,
    title character varying(200) NOT NULL,
    excerpt text,
    content text NOT NULL,
    meta_title character varying(200),
    meta_description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.journal_posts (
    id bigint NOT NULL,
    journal_category_id bigint NOT NULL,
    author_id bigint NOT NULL,
    slug character varying(150) NOT NULL,
    cover_image_url character varying(500),
    featured boolean DEFAULT false NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    reading_time_minutes integer DEFAULT 0 NOT NULL,
    published_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.product_journal_posts (
    product_id bigint NOT NULL,
    journal_post_id bigint NOT NULL
);

CREATE SEQUENCE public.journal_categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.journal_category_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.journal_post_translations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.journal_posts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.journal_categories_id_seq OWNED BY public.journal_categories.id;

ALTER SEQUENCE public.journal_category_translations_id_seq OWNED BY public.journal_category_translations.id;

ALTER SEQUENCE public.journal_post_translations_id_seq OWNED BY public.journal_post_translations.id;

ALTER SEQUENCE public.journal_posts_id_seq OWNED BY public.journal_posts.id;

ALTER TABLE ONLY public.journal_categories ALTER COLUMN id SET DEFAULT nextval('public.journal_categories_id_seq'::regclass);

ALTER TABLE ONLY public.journal_category_translations ALTER COLUMN id SET DEFAULT nextval('public.journal_category_translations_id_seq'::regclass);

ALTER TABLE ONLY public.journal_post_translations ALTER COLUMN id SET DEFAULT nextval('public.journal_post_translations_id_seq'::regclass);

ALTER TABLE ONLY public.journal_posts ALTER COLUMN id SET DEFAULT nextval('public.journal_posts_id_seq'::regclass);

ALTER TABLE ONLY public.journal_categories
    ADD CONSTRAINT journal_categories_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.journal_categories
    ADD CONSTRAINT journal_categories_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.journal_category_translations
    ADD CONSTRAINT journal_category_translations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.journal_category_translations
    ADD CONSTRAINT uk_journal_category_language UNIQUE (journal_category_id, language);

ALTER TABLE ONLY public.journal_post_tags
    ADD CONSTRAINT journal_post_tags_pkey PRIMARY KEY (journal_post_id, tag);

ALTER TABLE ONLY public.journal_post_translations
    ADD CONSTRAINT journal_post_translations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.journal_post_translations
    ADD CONSTRAINT uk_journal_post_language UNIQUE (journal_post_id, language);

ALTER TABLE ONLY public.journal_posts
    ADD CONSTRAINT journal_posts_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.journal_posts
    ADD CONSTRAINT journal_posts_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.product_journal_posts
    ADD CONSTRAINT product_journal_posts_pkey PRIMARY KEY (product_id, journal_post_id);
