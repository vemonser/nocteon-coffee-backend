-- Broadcasts and contact messages
-- Final schema for this domain. Do not edit after the database is baselined;
-- add a new versioned migration instead.

CREATE TABLE public.broadcast_recipients (
    id bigint NOT NULL,
    broadcast_id bigint NOT NULL,
    user_id bigint,
    email character varying(255),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    last_error text,
    provider_message_id character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.broadcasts (
    id bigint NOT NULL,
    subject character varying(255) NOT NULL,
    content text NOT NULL,
    status character varying(20) NOT NULL,
    total_recipients integer DEFAULT 0 NOT NULL,
    sent_count integer DEFAULT 0 NOT NULL,
    failed_count integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.contact_messages (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    email character varying(255) NOT NULL,
    subject character varying(200) NOT NULL,
    message text NOT NULL,
    is_read boolean DEFAULT false NOT NULL,
    is_replied boolean DEFAULT false NOT NULL,
    reply_content text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    status character varying(20) DEFAULT 'NEW'::character varying NOT NULL
);

CREATE SEQUENCE public.broadcast_recipients_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.broadcasts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.contact_messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.broadcast_recipients_id_seq OWNED BY public.broadcast_recipients.id;

ALTER SEQUENCE public.broadcasts_id_seq OWNED BY public.broadcasts.id;

ALTER SEQUENCE public.contact_messages_id_seq OWNED BY public.contact_messages.id;

ALTER TABLE ONLY public.broadcast_recipients ALTER COLUMN id SET DEFAULT nextval('public.broadcast_recipients_id_seq'::regclass);

ALTER TABLE ONLY public.broadcasts ALTER COLUMN id SET DEFAULT nextval('public.broadcasts_id_seq'::regclass);

ALTER TABLE ONLY public.contact_messages ALTER COLUMN id SET DEFAULT nextval('public.contact_messages_id_seq'::regclass);

ALTER TABLE ONLY public.broadcast_recipients
    ADD CONSTRAINT broadcast_recipients_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.broadcasts
    ADD CONSTRAINT broadcasts_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.contact_messages
    ADD CONSTRAINT contact_messages_pkey PRIMARY KEY (id);

CREATE INDEX idx_broadcast_recipients_broadcast ON public.broadcast_recipients USING btree (broadcast_id);

CREATE INDEX idx_broadcast_recipients_email ON public.broadcast_recipients USING btree (email);

CREATE INDEX idx_broadcast_recipients_provider_message_id ON public.broadcast_recipients USING btree (provider_message_id);

CREATE INDEX idx_broadcast_recipients_status ON public.broadcast_recipients USING btree (status);

CREATE INDEX idx_broadcast_recipients_user ON public.broadcast_recipients USING btree (user_id);

CREATE INDEX idx_broadcasts_created_at ON public.broadcasts USING btree (created_at DESC);

CREATE INDEX idx_broadcasts_status ON public.broadcasts USING btree (status);

CREATE INDEX idx_contact_messages_created_at ON public.contact_messages USING btree (created_at DESC);

CREATE UNIQUE INDEX uk_broadcast_recipients_broadcast_email ON public.broadcast_recipients USING btree (broadcast_id, email) WHERE (email IS NOT NULL);
