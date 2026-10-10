-- =========================================================
-- Enable pgvector
-- =========================================================
-- WHY THIS IS GUARDED RATHER THAN A PLAIN CREATE EXTENSION
-- A bare `CREATE EXTENSION vector;` fails on a server whose
-- PostgreSQL build does not ship the extension files, and Flyway
-- would then abort the whole migration run — taking every other
-- table in the project down with it. Search is one feature; the
-- storefront, the cart and the checkout are not optional.
--
-- So this is best-effort by design: if pgvector is present it is
-- enabled, and if it is not, the application still boots and every
-- other feature is unaffected. Semantic search then simply has no
-- vector store to query and stays disabled.
--
-- WHERE TO GET A DATABASE THAT WORKS
--   Locally, CI, or any VPS: the pgvector/pgvector:pg16 image used
--   by docker-compose.yml. This is the recommended setup.
--   Render managed Postgres: pgvector IS available on PostgreSQL 13
--   and later and is enabled by this very statement
--   (https://render.com/docs/postgresql-extensions).
--   Any managed provider that ships pgvector works equally well.
--
-- Whether the extension is present is a property of the PostgreSQL
-- server, not of this migration — so if this reports the "not
-- available" warning, the fix is to repoint DATABASE_HOST/PORT/
-- NAME/USERNAME/PASSWORD at a different server, not to change code.
-- =========================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        CREATE EXTENSION IF NOT EXISTS vector;
        RAISE NOTICE 'V103: pgvector extension is available and has been enabled.';
    ELSE
        RAISE WARNING 'V103: pgvector is not available on this PostgreSQL server, so the vector extension was NOT created. Semantic (vector) product search stays disabled on this database; everything else in the application is unaffected. To enable it, repoint the database settings at a pgvector-capable server — for example the pgvector/pgvector:pg16 image used by docker-compose.yml, or a managed provider such as Render, Neon or Supabase. No code change is required.';
    END IF;
END
$$;