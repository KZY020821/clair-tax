CREATE TABLE email_otp_codes (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    device_id VARCHAR(120),
    request_ip VARCHAR(64),
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_otp_codes_email_device_created_at
    ON email_otp_codes (email, device_id, created_at DESC);

CREATE INDEX idx_email_otp_codes_email_created_at
    ON email_otp_codes (email, created_at DESC);

CREATE INDEX idx_email_otp_codes_request_ip_created_at
    ON email_otp_codes (request_ip, created_at DESC);
