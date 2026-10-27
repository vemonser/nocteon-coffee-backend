-- In-app notifications and email log
-- Final schema for this domain. Do not edit after the database is baselined;
-- add a new versioned migration instead.

CREATE TABLE public.email_log (
    id bigint NOT NULL,
    idempotency_key character varying(255) NOT NULL,
    email_type character varying(50) NOT NULL,
    recipient_email character varying(255) NOT NULL,
    related_entity_type character varying(50),
    related_entity_id bigint,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    provider_message_id character varying(255),
    attempt_count integer DEFAULT 0 NOT NULL,
    last_error text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    sent_at timestamp without time zone
);

CREATE TABLE public.notifications (
    id bigint NOT NULL,
    type character varying(64) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    recipient_user_id bigint NOT NULL,
    params text,
    entity_type character varying(64),
    entity_id bigint,
    read_at timestamp without time zone
);

CREATE SEQUENCE public.email_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.email_log_id_seq OWNED BY public.email_log.id;

ALTER SEQUENCE public.notifications_id_seq OWNED BY public.notifications.id;

ALTER TABLE ONLY public.email_log ALTER COLUMN id SET DEFAULT nextval('public.email_log_id_seq'::regclass);

ALTER TABLE ONLY public.notifications ALTER COLUMN id SET DEFAULT nextval('public.notifications_id_seq'::regclass);

ALTER TABLE ONLY public.email_log
    ADD CONSTRAINT email_log_idempotency_key_key UNIQUE (idempotency_key);

ALTER TABLE ONLY public.email_log
    ADD CONSTRAINT email_log_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT uq_notifications_recipient_type_entity UNIQUE (recipient_user_id, type, entity_type, entity_id);

CREATE INDEX idx_email_log_related_entity ON public.email_log USING btree (related_entity_type, related_entity_id);

CREATE INDEX idx_email_log_status ON public.email_log USING btree (status);

CREATE INDEX idx_notifications_created_at ON public.notifications USING btree (created_at DESC);

CREATE INDEX idx_notifications_recipient_read_created ON public.notifications USING btree (recipient_user_id, read_at, created_at DESC);
