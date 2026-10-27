-- pgvector is optional: semantic search must not block the rest of the app.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        CREATE EXTENSION IF NOT EXISTS vector;
    ELSE
        RAISE WARNING 'pgvector is not available on this server; semantic search stays disabled.';
    END IF;
END
$$;
