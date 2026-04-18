CREATE TABLE magic_link_tokens (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_magic_link_tokens_email_created_at
    ON magic_link_tokens (email, created_at DESC);

CREATE TABLE web_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_web_sessions_user_id_created_at
    ON web_sessions (user_id, created_at DESC);

CREATE INDEX idx_web_sessions_active
    ON web_sessions (expires_at, revoked_at);
