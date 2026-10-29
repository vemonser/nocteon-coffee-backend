-- FE-13: the history of a stock number.
--
-- `product_variants.stock_quantity` is a single mutable column, so "why is this
-- 7 and not 12?" had no answer anywhere. Every change now writes a row here:
-- the count before, the count after, who did it and why.
--
-- before/after are stored, not only the delta: a reconciliation reads the pair,
-- and a stored pair can be checked against itself (the CHECK below) in a way a
-- delta alone cannot.
--
-- reason is the SOURCE of the change (an order consumed units, an admin edited
-- the number, a product was saved). reason_note is the human sentence an admin
-- must type for a manual adjustment — required by the API, nullable in the
-- column because an order-driven row has nobody to ask.
--
-- variant_id is a plain bigint and NOT a filtered relation: a variant that is
-- soft-deleted keeps its movements, and its history must stay readable.
--
-- No ON DELETE CASCADE: deleting a variant with history would silently erase the
-- audit trail. A variant is retired (deleted_at), never deleted, while it has
-- movements — the FK is here so that stays true in the database too.

CREATE TABLE public.inventory_movements (
    id bigint NOT NULL,
    variant_id bigint NOT NULL,
    stock_before integer NOT NULL,
    stock_after integer NOT NULL,
    delta integer NOT NULL,
    reason character varying(32) NOT NULL,
    reason_note character varying(500),
    actor character varying(255) NOT NULL,
    source_type character varying(32) NOT NULL,
    source_id bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT inventory_movements_pkey PRIMARY KEY (id),
    CONSTRAINT inventory_movements_delta_check CHECK (stock_after - stock_before = delta),
    CONSTRAINT inventory_movements_non_negative_check CHECK (stock_after >= 0 AND stock_before >= 0)
);

CREATE SEQUENCE public.inventory_movements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.inventory_movements_id_seq OWNED BY public.inventory_movements.id;

ALTER TABLE ONLY public.inventory_movements ALTER COLUMN id SET DEFAULT nextval('public.inventory_movements_id_seq'::regclass);

ALTER TABLE ONLY public.inventory_movements
    ADD CONSTRAINT inventory_movements_variant_id_fkey FOREIGN KEY (variant_id) REFERENCES public.product_variants(id);

-- The one read the variant panel makes: the last N movements of ONE variant,
-- newest first. The id breaks ties inside the same timestamp, so "the last
-- change" is never ambiguous when two rows share a millisecond.
CREATE INDEX idx_inventory_movements_variant_created
    ON public.inventory_movements (variant_id, created_at DESC, id DESC);

-- The reverse direction ("what did order 42 do to stock?") and the support the
-- foreign key would otherwise do a sequential scan for.
CREATE INDEX idx_inventory_movements_source ON public.inventory_movements (source_type, source_id);
