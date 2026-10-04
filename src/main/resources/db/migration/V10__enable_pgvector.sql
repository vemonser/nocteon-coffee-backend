-- Enables pgvector for semantic product search.
-- Spring AI's PgVectorStore creates and owns the vector_store table
-- and its HNSW index (see spring.ai.vectorstore.pgvector.* in application.yaml).
CREATE EXTENSION IF NOT EXISTS vector;
