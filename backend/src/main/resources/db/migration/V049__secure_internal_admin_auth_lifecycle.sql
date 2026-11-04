ALTER TABLE internal_admin_accounts
    ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 1;

CREATE TABLE internal_admin_sessions (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_account_id   UUID         NOT NULL REFERENCES internal_admin_accounts (id) ON DELETE CASCADE,
    csrf_token_hash    VARCHAR(64)  NOT NULL,
    credential_version BIGINT       NOT NULL,
    remember_me        BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at         TIMESTAMPTZ  NOT NULL,
    idle_expires_at    TIMESTAMPTZ  NOT NULL,
    last_used_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    revoked_at         TIMESTAMPTZ,
    user_agent         VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_internal_admin_session_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT chk_internal_admin_session_idle_expiry
        CHECK (idle_expires_at > created_at),
    CONSTRAINT chk_internal_admin_session_credential_version
        CHECK (credential_version > 0)
);

CREATE INDEX idx_internal_admin_sessions_account_active
    ON internal_admin_sessions (admin_account_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE internal_admin_refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID         NOT NULL REFERENCES internal_admin_sessions (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    replaced_by UUID         REFERENCES internal_admin_refresh_tokens (id),
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_internal_admin_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT chk_internal_admin_refresh_token_status
        CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED')),
    CONSTRAINT chk_internal_admin_refresh_token_expiry
        CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX uk_internal_admin_refresh_tokens_active_session
    ON internal_admin_refresh_tokens (session_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_internal_admin_refresh_tokens_session_created
    ON internal_admin_refresh_tokens (session_id, created_at DESC);

CREATE TABLE internal_admin_password_resets (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_account_id UUID         NOT NULL REFERENCES internal_admin_accounts (id) ON DELETE CASCADE,
    token_hash       VARCHAR(64)  NOT NULL,
    expires_at       TIMESTAMPTZ  NOT NULL,
    used_at          TIMESTAMPTZ,
    revoked_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_internal_admin_password_resets_token_hash
        UNIQUE (token_hash),
    CONSTRAINT chk_internal_admin_password_reset_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT chk_internal_admin_password_reset_terminal_state
        CHECK (used_at IS NULL OR revoked_at IS NULL)
);

CREATE INDEX idx_internal_admin_password_resets_account_created
    ON internal_admin_password_resets (admin_account_id, created_at DESC);

CREATE UNIQUE INDEX uk_internal_admin_password_resets_open_account
    ON internal_admin_password_resets (admin_account_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;

CREATE TABLE internal_admin_auth_rate_limits (
    rate_key          VARCHAR(64)  PRIMARY KEY,
    endpoint          VARCHAR(80)  NOT NULL,
    window_started_at TIMESTAMPTZ  NOT NULL,
    attempt_count     INTEGER      NOT NULL,
    blocked_until     TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_internal_admin_auth_rate_count
        CHECK (attempt_count > 0)
);

CREATE INDEX idx_internal_admin_auth_rate_limits_updated
    ON internal_admin_auth_rate_limits (updated_at);
