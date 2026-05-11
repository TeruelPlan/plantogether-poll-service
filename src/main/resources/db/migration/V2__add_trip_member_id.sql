-- Phase 1: introduce trip_member_id alongside device_id for cross-service identity migration.
-- Columns are nullable until backfill completes; legacy device_id columns remain in place
-- and are dual-written until Phase 3 (cleanup). See docs/PLAN_device_id_to_member_id.md.

ALTER TABLE poll
    ADD COLUMN created_by_trip_member_id UUID;

CREATE INDEX idx_poll_created_by_member ON poll (created_by_trip_member_id);

ALTER TABLE poll_response
    ADD COLUMN trip_member_id UUID;

CREATE INDEX idx_poll_response_member ON poll_response (trip_member_id);
