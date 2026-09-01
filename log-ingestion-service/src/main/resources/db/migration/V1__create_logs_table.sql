-- Logs reference their service by name rather than by foreign key: log events arrive from Kafka
-- and must be ingestible even for services that were never registered in the incident service.
CREATE TABLE logs (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    service_name VARCHAR(255) NOT NULL,
    level VARCHAR(20) NOT NULL,
    trace_id VARCHAR(100),
    message VARCHAR(4000) NOT NULL,
    exception VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Supports the dominant read pattern: "recent logs for this service", used by timeline
-- construction and RAG evidence retrieval.
CREATE INDEX idx_logs_service_occurred_at ON logs (service_name, occurred_at DESC);

-- Supports error-rate windows and level-filtered evidence lookups.
CREATE INDEX idx_logs_level_occurred_at ON logs (level, occurred_at DESC);

-- Supports correlating all log lines belonging to one request.
CREATE INDEX idx_logs_trace_id ON logs (trace_id);
