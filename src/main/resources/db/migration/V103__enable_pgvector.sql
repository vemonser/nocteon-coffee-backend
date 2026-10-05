-- Enables pgvector for semantic product search.
-- Spring AI's PgVectorStore creates and owns the vector_store table
-- and its HNSW index (see spring.ai.vectorstore.pgvector.* in application.yaml).
--
-- This is a DO block rather than a bare `CREATE EXTENSION` on purpose. The
-- extension is only a *precondition* for the AI feature, not for the
-- application: every other part of the AI feature degrades gracefully when
-- vector search is unavailable (AI_SEARCH_ENABLED=false by default, and
-- semanticCandidates() falls back to keyword ranking rather than throwing).
-- A bare `CREATE EXTENSION` would break that property, because pgvector is a
-- server-side binary and not every PostgreSQL has it — notably Render's
-- managed Postgres, which the `databases:` block in render.yaml provisions.
-- On such a server the bare statement fails with `extension "vector" is not
-- available`, Flyway aborts the whole migration, and the application refuses
-- to start — turning a disabled, optional feature into a deployment outage.
--
-- So the block branches on pg_available_extensions: when the extension is
-- present it is created exactly as before, and when it is absent the failure
-- is downgraded to a WARNING and the migration still succeeds. Vector search
-- stays unavailable in that case (nothing reads it while the flag is off),
-- and the WARNING names the fix. Re-running is safe: `IF NOT EXISTS` plus the
-- availability check make both branches idempotent.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        CREATE EXTENSION IF NOT EXISTS vector;
        RAISE NOTICE 'V103: pgvector extension is available and has been enabled.';
    ELSE
        RAISE WARNING 'V103: pgvector is not available on this PostgreSQL server, so the vector extension was NOT created. Semantic (vector) product search stays disabled on this database. Everything else in the application is unaffected. To enable it, point DATABASE_HOST/DATABASE_PORT/DATABASE_NAME/DATABASE_USERNAME/DATABASE_PASSWORD at a pgvector-capable database: the pgvector/pgvector:pg16 image used by docker-compose.yml locally and in CI, or a managed provider that ships pgvector (Neon, Supabase, or Koyeb). Note that Render managed Postgres does not provide pgvector, so the database declared in the `databases:` block of render.yaml cannot be used for this feature as currently configured.';
    END IF;
END
$$;