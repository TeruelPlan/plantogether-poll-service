-- Phase 3: drop legacy device_id columns now that all callers consume trip_member_id.
-- Dropping the device_id column also removes the legacy unique constraint
-- (poll_slot_id, device_id) and idx_poll_response_slot in cascade.
-- See docs/PROGRESS_device_id_to_member_id.md.

ALTER TABLE poll
    DROP COLUMN created_by;

ALTER TABLE poll
    ALTER COLUMN created_by_trip_member_id SET NOT NULL;

ALTER TABLE poll_response
    DROP COLUMN device_id;

ALTER TABLE poll_response
    ALTER COLUMN trip_member_id SET NOT NULL;

ALTER TABLE poll_response
    ADD CONSTRAINT uk_poll_response_slot_member UNIQUE (poll_slot_id, trip_member_id);
