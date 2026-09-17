-- Follow-up conversation per incident. Kept in the database rather than in memory so a conversation
-- survives a restart and can be reopened later during a long investigation.
CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    sources JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_messages_incident_created ON chat_messages (incident_id, created_at);
