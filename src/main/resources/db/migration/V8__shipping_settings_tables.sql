-- Shipping zones and store settings
-- Final schema for this domain. Do not edit after the database is baselined;
-- add a new versioned migration instead.

CREATE TABLE public.shipping_zone_cities (
    shipping_zone_id bigint NOT NULL,
    city character varying(100) NOT NULL
);

CREATE TABLE public.shipping_zones (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    shipping_cost numeric(10,2) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.store_settings (
    id bigint NOT NULL,
    free_shipping_threshold numeric(10,2),
    currency character varying(3) DEFAULT 'EGP'::character varying NOT NULL,
    is_free_shipping_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE public.shipping_zones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.store_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.shipping_zones_id_seq OWNED BY public.shipping_zones.id;

ALTER SEQUENCE public.store_settings_id_seq OWNED BY public.store_settings.id;

ALTER TABLE ONLY public.shipping_zones ALTER COLUMN id SET DEFAULT nextval('public.shipping_zones_id_seq'::regclass);

ALTER TABLE ONLY public.store_settings ALTER COLUMN id SET DEFAULT nextval('public.store_settings_id_seq'::regclass);

ALTER TABLE ONLY public.shipping_zone_cities
    ADD CONSTRAINT shipping_zone_cities_pkey PRIMARY KEY (shipping_zone_id, city);

ALTER TABLE ONLY public.shipping_zones
    ADD CONSTRAINT shipping_zones_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.store_settings
    ADD CONSTRAINT store_settings_pkey PRIMARY KEY (id);

CREATE INDEX idx_shipping_zone_cities_city ON public.shipping_zone_cities USING btree (city);
