-- Stored analyses. The evidence fingerprint lets a repeat request for an incident whose evidence has
-- not changed be answered from here instead of paying for another model call.
CREATE TABLE incident_analyses (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL,
    status VARCHAR(40) NOT NULL,
    model VARCHAR(100) NOT NULL,
    evidence_fingerprint VARCHAR(64) NOT NULL,
    result JSONB NOT NULL,
    prompt_tokens INT,
    completion_tokens INT,
    latency_ms BIGINT NOT NULL,
    model_calls INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_incident_analyses_incident_created ON incident_analyses (incident_id, created_at DESC);
CREATE INDEX idx_incident_analyses_fingerprint ON incident_analyses (incident_id, evidence_fingerprint, model);

-- The daily request budget per model, counted in the provider's quota day.
--
-- Held in the database rather than in memory so that a restart cannot reset the count and let the
-- service overspend the provider's daily quota. A slot is reserved with a single conditional UPDATE
-- (increment only while below the cap), which is atomic, so concurrent requests cannot both take
-- the last slot.
CREATE TABLE llm_daily_usage (
    usage_date DATE NOT NULL,
    model VARCHAR(100) NOT NULL,
    request_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (usage_date, model)
);

-- An audit trail of every attempted model call, including those the guard refused before they were
-- sent, so quota consumption can always be explained after the fact.
CREATE TABLE llm_call_log (
    id UUID PRIMARY KEY,
    model VARCHAR(100) NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    sent_to_provider BOOLEAN NOT NULL,
    detail VARCHAR(500),
    prompt_tokens INT,
    completion_tokens INT,
    latency_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_llm_call_log_created ON llm_call_log (created_at DESC);
