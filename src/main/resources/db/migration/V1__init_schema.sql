-- poll service: initial schema

CREATE TABLE poll (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    locked_slot_id UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_poll_trip_id ON poll (trip_id);

CREATE TABLE poll_slot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    slot_index INT NOT NULL
);

CREATE INDEX idx_poll_slot_poll_id ON poll_slot (poll_id);

CREATE TABLE poll_response (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_slot_id UUID NOT NULL,
    device_id UUID NOT NULL,
    status VARCHAR(10) NOT NULL,
    UNIQUE (poll_slot_id, device_id)
);

CREATE INDEX idx_poll_response_slot ON poll_response (poll_slot_id);
