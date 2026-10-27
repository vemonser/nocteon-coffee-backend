-- Audit log and its updated-at trigger
-- Final schema for this domain. Do not edit after the database is baselined;
-- add a new versioned migration instead.

CREATE TABLE public.audit_logs (
    id bigint NOT NULL,
    user_id bigint,
    user_email character varying(255),
    action character varying(50) NOT NULL,
    entity_type character varying(50),
    entity_id bigint,
    entity_slug character varying(200),
    ip_address character varying(45),
    user_agent text,
    old_values text,
    new_values text,
    metadata text,
    success boolean DEFAULT true NOT NULL,
    error_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE SEQUENCE public.audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.audit_logs_id_seq OWNED BY public.audit_logs.id;

ALTER TABLE ONLY public.audit_logs ALTER COLUMN id SET DEFAULT nextval('public.audit_logs_id_seq'::regclass);

CREATE FUNCTION public.update_updated_at_column() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);

CREATE INDEX idx_audit_log_action ON public.audit_logs USING btree (action);

CREATE INDEX idx_audit_log_created ON public.audit_logs USING btree (created_at);

CREATE INDEX idx_audit_log_entity ON public.audit_logs USING btree (entity_type, entity_id);

CREATE INDEX idx_audit_log_user ON public.audit_logs USING btree (user_id);

CREATE TRIGGER update_audit_logs_updated_at BEFORE UPDATE ON public.audit_logs FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
