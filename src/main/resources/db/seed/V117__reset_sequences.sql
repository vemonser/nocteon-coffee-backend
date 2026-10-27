-- Demo data for local development and CI. This location is excluded in production.
-- Depends only on db/migration schema + reference data.
--
-- Seed rows use explicit ids, so every owned sequence must be advanced past the
-- maximum seeded id. Without this, the first application-created row reuses id 1
-- and fails on the primary key. order_number_seq is not owned by a table, so it
-- is advanced separately from the seeded ORD-YYYY-NNNNNN numbers.

DO $$
DECLARE
    r record;
    max_id bigint;
BEGIN
    FOR r IN
        SELECT n.nspname AS schema_name,
               c.relname AS table_name,
               a.attname AS column_name,
               pg_get_serial_sequence(format('%I.%I', n.nspname, c.relname), a.attname) AS seq
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
        WHERE c.relkind = 'r'
          AND n.nspname = 'public'
          AND pg_get_serial_sequence(format('%I.%I', n.nspname, c.relname), a.attname) IS NOT NULL
    LOOP
        EXECUTE format('SELECT COALESCE(MAX(%I), 0) FROM %I.%I',
                       r.column_name, r.schema_name, r.table_name) INTO max_id;
        IF max_id > 0 THEN
            PERFORM setval(r.seq::regclass, max_id, true);
        ELSE
            PERFORM setval(r.seq::regclass, 1, false);
        END IF;
    END LOOP;
END
$$;

-- The order number is generated from a standalone sequence, not an owned serial.
SELECT setval(
    'public.order_number_seq',
    COALESCE((SELECT MAX(split_part(order_number, '-', 3)::bigint) FROM public.orders), 1),
    EXISTS (SELECT 1 FROM public.orders)
);
