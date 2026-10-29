-- FE-20: returns / RMA — what came back, and the stock it puts back.
--
-- A refund is MONEY (order_refunds). A return is GOODS: which lines of which
-- order came back, how many of them, and why. The two are different facts and
-- the store needs both, so this is a new pair of tables rather than a column
-- on the refund.
--
-- The approval is the moment stock comes back. It is NOT recorded here as a
-- second ledger: the restock is written through inventory_movements (FE-13),
-- with reason RETURN and source RETURN pointing at returns.id. One log of
-- stock movement, whichever path moved it.
--
-- reason is NOT NULL: "why did this come back?" is the question the operator
-- answers a week later, and a return without one is a hole in the record.
--
-- refund_id is nullable and points at the refund that pays the return back —
-- when the money was returned at all. A return whose refund was never recorded
-- (or an exchange) keeps it null instead of pretending a refund exists.
--
-- No ON DELETE CASCADE anywhere: deleting an order, an item or a refund must
-- not silently erase the returns they explain. The rows are history.

CREATE TABLE public.returns (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    status character varying(32) NOT NULL,
    reason character varying(500) NOT NULL,
    requested_by character varying(255) NOT NULL,
    decided_by character varying(255),
    decided_at timestamp with time zone,
    refund_id bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT returns_pkey PRIMARY KEY (id),
    -- A decided return says who decided it and when; a requested one says
    -- neither. Half a decision is not a state the flow can reach.
    CONSTRAINT returns_decision_check CHECK (
        (status = 'REQUESTED' AND decided_by IS NULL AND decided_at IS NULL)
        OR (status <> 'REQUESTED' AND decided_by IS NOT NULL AND decided_at IS NOT NULL)
    )
);

CREATE SEQUENCE public.returns_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.returns_id_seq OWNED BY public.returns.id;

ALTER TABLE ONLY public.returns ALTER COLUMN id SET DEFAULT nextval('public.returns_id_seq'::regclass);

CREATE TABLE public.return_items (
    id bigint NOT NULL,
    return_id bigint NOT NULL,
    order_item_id bigint NOT NULL,
    quantity integer NOT NULL,
    note character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT return_items_pkey PRIMARY KEY (id),
    -- Zero units returned is not a line; a negative one would restock stock
    -- that never left the shelf.
    CONSTRAINT return_items_quantity_check CHECK (quantity > 0),
    -- One line per order item per return: two lines for the same item would
    -- make "how many of this item came back" a sum the reader has to do.
    CONSTRAINT return_items_return_item_key UNIQUE (return_id, order_item_id)
);

CREATE SEQUENCE public.return_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.return_items_id_seq OWNED BY public.return_items.id;

ALTER TABLE ONLY public.return_items ALTER COLUMN id SET DEFAULT nextval('public.return_items_id_seq'::regclass);

ALTER TABLE ONLY public.returns
    ADD CONSTRAINT returns_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id);

ALTER TABLE ONLY public.returns
    ADD CONSTRAINT returns_refund_id_fkey FOREIGN KEY (refund_id) REFERENCES public.order_refunds(id);

ALTER TABLE ONLY public.return_items
    ADD CONSTRAINT return_items_return_id_fkey FOREIGN KEY (return_id) REFERENCES public.returns(id);

ALTER TABLE ONLY public.return_items
    ADD CONSTRAINT return_items_order_item_id_fkey FOREIGN KEY (order_item_id) REFERENCES public.order_items(id);

-- The one read the order page makes: the returns of ONE order, newest first.
-- The id breaks ties inside the same timestamp, so "the latest return" is
-- never ambiguous when two rows share a millisecond.
CREATE INDEX idx_returns_order_created ON public.returns (order_id, created_at DESC, id DESC);

-- The reverse direction ("was anything ever returned?" / the refund link) and
-- the support the foreign keys would otherwise do a sequential scan for.
CREATE INDEX idx_returns_refund ON public.returns (refund_id);
CREATE INDEX idx_return_items_return ON public.return_items (return_id);
-- Used by the over-quantity guard: how many units of this order item already
-- came back on returns that were not rejected.
CREATE INDEX idx_return_items_order_item ON public.return_items (order_item_id);

-- FE-20: the return also belongs on the order's TIMELINE.
--
-- The timeline is order_status_history (one table, read by
-- GET /dashboard/orders/{id}/history and drawn by the order page). A return
-- does not move the order's status, so its row carries event_type =
-- RETURN_REQUESTED / RETURN_APPROVED / RETURN_REJECTED, a null from_status
-- (nothing was transitioned) and the order's CURRENT status in to_status —
-- the truth "this happened while the order was CONFIRMED", not a fabricated
-- transition. Readers that draw the status CHAIN skip rows whose event_type
-- is set; the timeline shows them. No second timeline table, no invented
-- status value in a column that means status.
ALTER TABLE public.order_status_history
    ADD COLUMN event_type character varying(32);
