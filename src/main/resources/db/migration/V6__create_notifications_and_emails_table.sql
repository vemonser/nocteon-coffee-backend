CREATE TABLE
    notifications (
        id BIGSERIAL PRIMARY KEY,
        type VARCHAR(50) NOT NULL,
        title VARCHAR(255) NOT NULL,
        message TEXT,
        link VARCHAR(255),
        is_read BOOLEAN NOT NULL DEFAULT FALSE,
        created_at TIMESTAMP NOT NULL DEFAULT now (),
        updated_at TIMESTAMP NOT NULL DEFAULT now ()
    );

CREATE INDEX idx_notifications_is_read ON notifications (is_read);
CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);

CREATE TABLE email_log (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE, 
    email_type VARCHAR(50) NOT NULL,               
    recipient_email VARCHAR(255) NOT NULL,
    related_entity_type VARCHAR(50),               
    related_entity_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  
    provider_message_id VARCHAR(255),               
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    sent_at TIMESTAMP
);

CREATE INDEX idx_email_log_status ON email_log(status);
CREATE INDEX idx_email_log_related_entity ON email_log(related_entity_type, related_entity_id);
