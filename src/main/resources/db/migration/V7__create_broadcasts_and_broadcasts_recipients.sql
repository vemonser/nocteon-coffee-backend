CREATE TABLE
    broadcasts (
        id BIGSERIAL PRIMARY KEY,
        subject VARCHAR(255) NOT NULL,
        content TEXT NOT NULL,
        status VARCHAR(20) NOT NULL,
        total_recipients INTEGER NOT NULL DEFAULT 0,
        sent_count INTEGER NOT NULL DEFAULT 0,
        failed_count INTEGER NOT NULL DEFAULT 0,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW ()
    );

CREATE INDEX idx_broadcasts_status ON broadcasts (status);

CREATE INDEX idx_broadcasts_created_at ON broadcasts (created_at DESC);

CREATE TABLE
    broadcast_recipients (
        id BIGSERIAL PRIMARY KEY,
        broadcast_id BIGINT NOT NULL,
        user_id BIGINT NOT NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
        attempt_count INTEGER NOT NULL DEFAULT 0,
        last_error TEXT,
        provider_message_id VARCHAR(255),
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW (),
        CONSTRAINT fk_broadcast_recipients_broadcast FOREIGN KEY (broadcast_id) REFERENCES broadcasts (id) ON DELETE CASCADE,
        CONSTRAINT fk_broadcast_recipients_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
    );

CREATE INDEX idx_broadcast_recipients_broadcast ON broadcast_recipients (broadcast_id);

CREATE INDEX idx_broadcast_recipients_user ON broadcast_recipients (user_id);

CREATE INDEX idx_broadcast_recipients_status ON broadcast_recipients (status);

CREATE INDEX idx_broadcast_recipients_provider_message_id ON broadcast_recipients (provider_message_id);

CREATE UNIQUE INDEX uk_broadcast_recipients_broadcast_user ON broadcast_recipients (broadcast_id, user_id);