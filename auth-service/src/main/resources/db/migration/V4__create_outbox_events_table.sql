-- ============================================================
-- V4: Transactional Outbox Table
-- ============================================================

CREATE TABLE IF NOT EXISTS outbox_events (
                                             id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
    );

CREATE INDEX idx_outbox_status
    ON outbox_events(status);

CREATE INDEX idx_outbox_created_at
    ON outbox_events(created_at);

COMMENT ON TABLE outbox_events IS 'Transactional outbox table for reliable event publishing';
COMMENT ON COLUMN outbox_events.payload IS 'JSON serialized domain event';
COMMENT ON COLUMN outbox_events.status IS 'PENDING | SENT | FAILED';