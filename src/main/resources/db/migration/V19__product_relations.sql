-- "You may also like": a curated, ordered relation between products.
--
-- Pairings (product_pairings) are taste pairings — what to drink it with. This
-- is merchandising: what else to look at, often across categories, chosen by an
-- admin. A product carries at most six, and the order is the admin's, so the
-- join table keeps a position column.

CREATE TABLE public.product_relations (
    product_id bigint NOT NULL,
    related_product_id bigint NOT NULL,
    position integer NOT NULL,
    CONSTRAINT product_relations_pkey PRIMARY KEY (product_id, related_product_id),
    CONSTRAINT product_relations_no_self CHECK (product_id <> related_product_id)
);

ALTER TABLE ONLY public.product_relations
    ADD CONSTRAINT product_relations_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_relations
    ADD CONSTRAINT product_relations_related_product_id_fkey FOREIGN KEY (related_product_id) REFERENCES public.products(id) ON DELETE CASCADE;

-- The relation is read from the product side; the reverse index serves the
-- "used by" checks and keeps the foreign key from scanning.
CREATE INDEX idx_product_relations_product ON public.product_relations (product_id);
CREATE INDEX idx_product_relations_related ON public.product_relations (related_product_id);
