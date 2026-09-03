-- Deployments and incidents can now originate from Kafka events. The producer-assigned event id,
-- with a unique constraint, makes at-least-once redelivery a no-op instead of a duplicate row.
-- Nullable because records created directly through the REST API have no originating event.
ALTER TABLE deployments ADD COLUMN event_id UUID;
ALTER TABLE deployments ADD CONSTRAINT uq_deployments_event_id UNIQUE (event_id);

ALTER TABLE incidents ADD COLUMN event_id UUID;
ALTER TABLE incidents ADD CONSTRAINT uq_incidents_event_id UNIQUE (event_id);

-- Detection metadata, kept so an incident can always be traced back to the rule that opened it.
ALTER TABLE incidents ADD COLUMN detection_rule VARCHAR(100);

-- Supports the "is there already an active incident for this service?" dedup check.
CREATE INDEX idx_incidents_service_status_detected ON incidents (service_id, status, detected_at DESC);
