-- Carts, orders, payments, promo codes and reviews
-- Final schema for this domain. Do not edit after the database is baselined;
-- add a new versioned migration instead.

CREATE TABLE public.addresses (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    full_name character varying(100) NOT NULL,
    phone character varying(20) NOT NULL,
    street character varying(255) NOT NULL,
    city character varying(100) NOT NULL,
    state character varying(100),
    country character varying(100) NOT NULL,
    postal_code character varying(20),
    is_default boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.cart_items (
    id bigint NOT NULL,
    cart_id bigint NOT NULL,
    product_variant_id bigint NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.carts (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    last_reminded_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    last_activity_at timestamp with time zone,
    converted_order_id bigint
);

CREATE TABLE public.newsletter_subscribers (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    token_hash character varying(255) NOT NULL,
    subscribed boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.order_items (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    product_variant_id bigint NOT NULL,
    promo_code_id bigint,
    discount_amount numeric(10,2),
    quantity integer NOT NULL,
    unit_price numeric(10,2) NOT NULL,
    total_price numeric(10,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.order_notes (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    author character varying(255) NOT NULL,
    body text NOT NULL,
    is_internal boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.order_refunds (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    payment_id bigint,
    amount numeric(10,2) NOT NULL,
    reason character varying(500),
    created_by character varying(255),
    gateway_reference character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.order_status_history (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    from_status character varying(50),
    to_status character varying(50) NOT NULL,
    note text,
    changed_by character varying(255),
    notify_customer boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.orders (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    address_id bigint NOT NULL,
    order_number character varying(50),
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    payment_status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    payment_method character varying(50) DEFAULT 'ONLINE'::character varying NOT NULL,
    shipping_cost numeric(10,2) DEFAULT 0 NOT NULL,
    promo_code_id bigint,
    total_amount numeric(10,2) NOT NULL,
    discount_amount numeric(10,2),
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    carrier character varying(120),
    tracking_number character varying(120),
    tracking_url character varying(500),
    shipped_at timestamp with time zone,
    delivered_at timestamp with time zone,
    estimated_delivery_at timestamp with time zone,
    cancellation_reason character varying(500)
);

CREATE TABLE public.payments (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    provider character varying(50) DEFAULT 'PAYMOB'::character varying NOT NULL,
    provider_payment_id character varying(255),
    provider_order_id character varying(255),
    amount numeric(10,2) NOT NULL,
    currency character varying(10) DEFAULT 'EGP'::character varying NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    payment_method character varying(50),
    failure_reason text,
    attempt_number integer DEFAULT 1 NOT NULL,
    paid_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.promo_code_categories (
    promo_code_id bigint NOT NULL,
    category_id bigint NOT NULL
);

CREATE TABLE public.promo_code_redemptions (
    id bigint NOT NULL,
    promo_code_id bigint NOT NULL,
    user_id bigint NOT NULL,
    order_id bigint NOT NULL,
    discount_amount numeric(10,2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.promo_codes (
    id bigint NOT NULL,
    code character varying(50) NOT NULL,
    discount_type character varying(20) NOT NULL,
    discount_value numeric(10,2),
    min_order_amount numeric(10,2),
    scope_type character varying(20) NOT NULL,
    max_total_redemptions integer,
    max_redemptions_per_user integer DEFAULT 1 NOT NULL,
    valid_from timestamp with time zone NOT NULL,
    valid_until timestamp with time zone NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);

CREATE TABLE public.reviews (
    id bigint NOT NULL,
    product_id bigint NOT NULL,
    user_id bigint NOT NULL,
    rating integer NOT NULL,
    comment text,
    is_verified boolean DEFAULT false NOT NULL,
    is_approved boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    CONSTRAINT reviews_rating_check CHECK (((rating >= 1) AND (rating <= 5)))
);

CREATE TABLE public.wishlist_items (
    id bigint NOT NULL,
    wishlist_id bigint NOT NULL,
    product_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.wishlists (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE public.addresses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.cart_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.carts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.newsletter_subscribers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.order_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.order_notes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.order_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.order_refunds_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.order_status_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.payments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.promo_code_redemptions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.promo_codes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.reviews_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.wishlist_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.wishlists_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.addresses_id_seq OWNED BY public.addresses.id;

ALTER SEQUENCE public.cart_items_id_seq OWNED BY public.cart_items.id;

ALTER SEQUENCE public.carts_id_seq OWNED BY public.carts.id;

ALTER SEQUENCE public.newsletter_subscribers_id_seq OWNED BY public.newsletter_subscribers.id;

ALTER SEQUENCE public.order_items_id_seq OWNED BY public.order_items.id;

ALTER SEQUENCE public.order_notes_id_seq OWNED BY public.order_notes.id;

ALTER SEQUENCE public.order_refunds_id_seq OWNED BY public.order_refunds.id;

ALTER SEQUENCE public.order_status_history_id_seq OWNED BY public.order_status_history.id;

ALTER SEQUENCE public.orders_id_seq OWNED BY public.orders.id;

ALTER SEQUENCE public.payments_id_seq OWNED BY public.payments.id;

ALTER SEQUENCE public.promo_code_redemptions_id_seq OWNED BY public.promo_code_redemptions.id;

ALTER SEQUENCE public.promo_codes_id_seq OWNED BY public.promo_codes.id;

ALTER SEQUENCE public.reviews_id_seq OWNED BY public.reviews.id;

ALTER SEQUENCE public.wishlist_items_id_seq OWNED BY public.wishlist_items.id;

ALTER SEQUENCE public.wishlists_id_seq OWNED BY public.wishlists.id;

ALTER TABLE ONLY public.addresses ALTER COLUMN id SET DEFAULT nextval('public.addresses_id_seq'::regclass);

ALTER TABLE ONLY public.cart_items ALTER COLUMN id SET DEFAULT nextval('public.cart_items_id_seq'::regclass);

ALTER TABLE ONLY public.carts ALTER COLUMN id SET DEFAULT nextval('public.carts_id_seq'::regclass);

ALTER TABLE ONLY public.newsletter_subscribers ALTER COLUMN id SET DEFAULT nextval('public.newsletter_subscribers_id_seq'::regclass);

ALTER TABLE ONLY public.order_items ALTER COLUMN id SET DEFAULT nextval('public.order_items_id_seq'::regclass);

ALTER TABLE ONLY public.order_notes ALTER COLUMN id SET DEFAULT nextval('public.order_notes_id_seq'::regclass);

ALTER TABLE ONLY public.order_refunds ALTER COLUMN id SET DEFAULT nextval('public.order_refunds_id_seq'::regclass);

ALTER TABLE ONLY public.order_status_history ALTER COLUMN id SET DEFAULT nextval('public.order_status_history_id_seq'::regclass);

ALTER TABLE ONLY public.orders ALTER COLUMN id SET DEFAULT nextval('public.orders_id_seq'::regclass);

ALTER TABLE ONLY public.payments ALTER COLUMN id SET DEFAULT nextval('public.payments_id_seq'::regclass);

ALTER TABLE ONLY public.promo_code_redemptions ALTER COLUMN id SET DEFAULT nextval('public.promo_code_redemptions_id_seq'::regclass);

ALTER TABLE ONLY public.promo_codes ALTER COLUMN id SET DEFAULT nextval('public.promo_codes_id_seq'::regclass);

ALTER TABLE ONLY public.reviews ALTER COLUMN id SET DEFAULT nextval('public.reviews_id_seq'::regclass);

ALTER TABLE ONLY public.wishlist_items ALTER COLUMN id SET DEFAULT nextval('public.wishlist_items_id_seq'::regclass);

ALTER TABLE ONLY public.wishlists ALTER COLUMN id SET DEFAULT nextval('public.wishlists_id_seq'::regclass);

ALTER TABLE ONLY public.addresses
    ADD CONSTRAINT addresses_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT cart_items_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT uk_cart_variant UNIQUE (cart_id, product_variant_id);

ALTER TABLE ONLY public.carts
    ADD CONSTRAINT carts_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.carts
    ADD CONSTRAINT carts_user_id_key UNIQUE (user_id);

ALTER TABLE ONLY public.newsletter_subscribers
    ADD CONSTRAINT newsletter_subscribers_email_key UNIQUE (email);

ALTER TABLE ONLY public.newsletter_subscribers
    ADD CONSTRAINT newsletter_subscribers_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.order_notes
    ADD CONSTRAINT order_notes_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.order_refunds
    ADD CONSTRAINT order_refunds_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.order_status_history
    ADD CONSTRAINT order_status_history_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_order_number_key UNIQUE (order_number);

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.promo_code_categories
    ADD CONSTRAINT promo_code_categories_pkey PRIMARY KEY (promo_code_id, category_id);

ALTER TABLE ONLY public.promo_code_redemptions
    ADD CONSTRAINT promo_code_redemptions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.promo_code_redemptions
    ADD CONSTRAINT uk_promo_redemption_order UNIQUE (order_id);

ALTER TABLE ONLY public.promo_codes
    ADD CONSTRAINT promo_codes_code_key UNIQUE (code);

ALTER TABLE ONLY public.promo_codes
    ADD CONSTRAINT promo_codes_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT uk_user_product_review UNIQUE (user_id, product_id);

ALTER TABLE ONLY public.wishlist_items
    ADD CONSTRAINT uk_wishlist_product UNIQUE (wishlist_id, product_id);

ALTER TABLE ONLY public.wishlist_items
    ADD CONSTRAINT wishlist_items_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.wishlists
    ADD CONSTRAINT wishlists_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.wishlists
    ADD CONSTRAINT wishlists_user_id_key UNIQUE (user_id);

CREATE INDEX idx_carts_converted_order_id ON public.carts USING btree (converted_order_id);

CREATE INDEX idx_carts_last_activity_at ON public.carts USING btree (last_activity_at);

CREATE INDEX idx_carts_status ON public.carts USING btree (status);

CREATE INDEX idx_newsletter_subscribers_subscribed ON public.newsletter_subscribers USING btree (subscribed);

CREATE INDEX idx_order_items_order_id ON public.order_items USING btree (order_id);

CREATE INDEX idx_order_notes_order ON public.order_notes USING btree (order_id, created_at);

CREATE INDEX idx_order_refunds_order ON public.order_refunds USING btree (order_id, created_at);

CREATE INDEX idx_order_status_history_order ON public.order_status_history USING btree (order_id, created_at);

CREATE INDEX idx_orders_created_at ON public.orders USING btree (created_at);

CREATE INDEX idx_orders_order_number ON public.orders USING btree (order_number);

CREATE INDEX idx_orders_payment_method_created_at ON public.orders USING btree (payment_method, created_at);

CREATE INDEX idx_orders_payment_status_created_at ON public.orders USING btree (payment_status, created_at);

CREATE INDEX idx_orders_status_created_at ON public.orders USING btree (status, created_at);

CREATE INDEX idx_promo_codes_code ON public.promo_codes USING btree (code);

CREATE INDEX idx_promo_redemptions_order ON public.promo_code_redemptions USING btree (order_id);

CREATE INDEX idx_promo_redemptions_promo_code ON public.promo_code_redemptions USING btree (promo_code_id);

CREATE INDEX idx_promo_redemptions_user ON public.promo_code_redemptions USING btree (user_id);

CREATE INDEX idx_promo_redemptions_user_promo ON public.promo_code_redemptions USING btree (user_id, promo_code_id);

CREATE INDEX idx_reviews_is_approved ON public.reviews USING btree (is_approved) WHERE (deleted_at IS NULL);
