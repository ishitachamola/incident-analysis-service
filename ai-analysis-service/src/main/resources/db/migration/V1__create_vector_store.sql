-- Extensions are database-wide objects and are installed once, into public, rather than per schema.
-- Types and operator classes they provide are therefore referenced as public.* below, so this
-- migration succeeds regardless of the search_path in effect when it runs.
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

-- Storage for embedded knowledge-base chunks (runbooks and historical incidents).
-- The column layout is the one Spring AI's PgVectorStore expects. It is defined here rather than
-- left to the library's own schema initialisation so that, as everywhere else in this platform,
-- Flyway remains the single owner of database schema and every change is reviewable and versioned.
--
-- The embedding dimension (1536) is fixed by the embedding model in use
-- (OpenAI text-embedding-3-small). Changing models means changing this column and re-embedding
-- every document, so the dimension is a deliberate, migration-visible decision.
CREATE TABLE vector_store (
    id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
    content TEXT NOT NULL,
    metadata JSONB,
    embedding public.vector(1536)
);

-- HNSW gives better recall/latency than IVFFlat at this corpus size and, unlike IVFFlat, needs no
-- training pass over existing rows before it is useful. Cosine distance matches how the embedding
-- model normalises its vectors.
CREATE INDEX idx_vector_store_embedding ON vector_store
    USING HNSW (embedding public.vector_cosine_ops);

-- Retrieval pre-filters on document type and service before the similarity search, so the metadata
-- lookups need their own index.
CREATE INDEX idx_vector_store_metadata ON vector_store USING GIN (metadata);

-- Tracks which source files have been ingested, so re-running ingestion can replace a document's
-- chunks instead of silently duplicating them.
CREATE TABLE ingested_documents (
    id UUID PRIMARY KEY,
    source VARCHAR(500) NOT NULL UNIQUE,
    document_type VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    service_name VARCHAR(255),
    content_hash VARCHAR(64) NOT NULL,
    chunk_count INT NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
