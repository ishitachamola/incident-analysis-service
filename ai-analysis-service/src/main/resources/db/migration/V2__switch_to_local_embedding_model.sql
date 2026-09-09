-- Switches the embedding model from a hosted one (1536 dimensions) to a local in-process model
-- (all-MiniLM-L6-v2, 384 dimensions).
--
-- Embedding dimension is not a cosmetic change. Vectors produced by different models are not
-- comparable, so every stored embedding is invalidated by this switch and the whole corpus must be
-- re-embedded. Clearing the ingestion tracking table is what makes that happen: with no recorded
-- content hash, the next ingestion run treats every document as new.

-- Existing vectors are meaningless under the new model, so they go rather than being migrated.
DELETE FROM vector_store;
DELETE FROM ingested_documents;

-- The index depends on the column's dimension and must be rebuilt around the new one.
DROP INDEX IF EXISTS idx_vector_store_embedding;

ALTER TABLE vector_store
    ALTER COLUMN embedding TYPE public.vector(384);

CREATE INDEX idx_vector_store_embedding ON vector_store
    USING HNSW (embedding public.vector_cosine_ops);
