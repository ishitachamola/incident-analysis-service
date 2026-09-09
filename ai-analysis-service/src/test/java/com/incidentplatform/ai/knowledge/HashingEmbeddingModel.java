package com.incidentplatform.ai.knowledge;

import java.util.List;
import java.util.Locale;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * A deterministic, offline stand-in for a real embedding model, used so the test suite needs no API
 * key, costs nothing to run, and cannot fail because of a network problem.
 *
 * <p>It implements feature hashing: each word is hashed to a dimension and accumulated, then the
 * vector is L2-normalised. That is far weaker than a real embedding — it captures vocabulary
 * overlap, not meaning, so it cannot match a paraphrase that shares no words. It is nonetheless
 * enough to verify what these tests are actually about: that chunking, storage, metadata filtering
 * and nearest-neighbour ranking are wired together correctly. Judging genuine semantic quality
 * requires the real model and belongs in the evaluation milestone.
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 1536;

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        for (String token : tokenize(text)) {
            int index = Math.floorMod(token.hashCode(), DIMENSIONS);
            // A sign derived from a second hash keeps unrelated collisions from always reinforcing.
            vector[index] += Math.floorMod(token.hashCode() * 31, 2) == 0 ? 1f : -1f;
        }
        return normalise(vector);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = request.getInstructions().stream()
                .map(this::embed)
                .map(vector -> new Embedding(vector, 0))
                .toList();
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private List<String> tokenize(String text) {
        return List.of(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")).stream()
                .filter(token -> token.length() > 2)
                .toList();
    }

    private float[] normalise(float[] vector) {
        double magnitude = 0;
        for (float value : vector) {
            magnitude += value * value;
        }
        magnitude = Math.sqrt(magnitude);
        if (magnitude == 0) {
            // pgvector cannot compute cosine distance against a zero vector.
            vector[0] = 1f;
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= (float) magnitude;
        }
        return vector;
    }
}
