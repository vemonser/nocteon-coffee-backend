-- "You may also like": a curated, ordered relation between products.
--
-- Pairings (product_pairings) are taste pairings — what to drink it with. This
-- is merchandising: what else to look at, often across categories, chosen by an
-- admin. A product carries at most six, and the order is the admin's.
--
-- An explicit row (rather than an @OrderColumn join collection) because the
-- order has to be rewritten when the admin re-picks: Hibernate's ordered
-- collection updates positions in place and collides on the primary key as soon
-- as two entries swap. The service replaces the rows of a product instead, which
-- is one delete plus N inserts regardless of what changed.

CREATE TABLE public.product_relations (
    id bigint NOT NULL,
    product_id bigint NOT NULL,
    related_product_id bigint NOT NULL,
    position integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT product_relations_pkey PRIMARY KEY (id),
    CONSTRAINT product_relations_pair_key UNIQUE (product_id, related_product_id),
    CONSTRAINT product_relations_no_self CHECK (product_id <> related_product_id)
);

CREATE SEQUENCE public.product_relations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.product_relations_id_seq OWNED BY public.product_relations.id;

ALTER TABLE ONLY public.product_relations ALTER COLUMN id SET DEFAULT nextval('public.product_relations_id_seq'::regclass);

ALTER TABLE ONLY public.product_relations
    ADD CONSTRAINT product_relations_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_relations
    ADD CONSTRAINT product_relations_related_product_id_fkey FOREIGN KEY (related_product_id) REFERENCES public.products(id) ON DELETE CASCADE;

-- Read from the product side, in position order.
CREATE INDEX idx_product_relations_product ON public.product_relations (product_id, position);

-- The reverse direction serves "where is this product referenced", and keeps the
-- foreign key from scanning the table on a product delete.
CREATE INDEX idx_product_relations_related ON public.product_relations (related_product_id);
