CREATE TABLE internal_admin_invitations (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_account_id UUID         NOT NULL REFERENCES internal_admin_accounts (id) ON DELETE CASCADE,
    token_hash       VARCHAR(64)  NOT NULL,
    created_by       UUID         NOT NULL REFERENCES internal_admin_accounts (id),
    expires_at       TIMESTAMPTZ  NOT NULL,
    used_at          TIMESTAMPTZ,
    revoked_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_internal_admin_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_internal_admin_invitation_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT chk_internal_admin_invitation_terminal_state
        CHECK (used_at IS NULL OR revoked_at IS NULL)
);

CREATE INDEX idx_internal_admin_invitations_account_created
    ON internal_admin_invitations (admin_account_id, created_at DESC);

CREATE UNIQUE INDEX uk_internal_admin_invitations_open_account
    ON internal_admin_invitations (admin_account_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;
