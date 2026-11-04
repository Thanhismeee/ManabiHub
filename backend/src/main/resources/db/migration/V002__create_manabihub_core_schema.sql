-- ============================================================================
-- V002__create_manabihub_core_schema.sql
-- MHB-58: ManabiHub core schema after council feedback
--
-- Domains:
--   1. Identity + RBAC
--   2. System Settings + Audit
--   3. Profile + Teacher KYC
--   4. Course + Content + Approval
--   5. Enrollment + Learning
--   6. AI Writing (Suggestion-Only — NOT grading/scoring)
--   7. Commerce + Wallet + Refund + Payout
--   8. Moderation + Notification
--
-- Conventions:
--   - UUID primary keys via gen_random_uuid() (pgcrypto enabled in V001)
--   - VARCHAR + CHECK for status fields (no PostgreSQL enum types)
--   - snake_case naming
--   - created_at / updated_at on all tables
--   - Indexes on FKs, status, created_at, email, code, slug
-- ============================================================================

-- ============================================================================
-- PART 1: IDENTITY + RBAC
-- ============================================================================

-- 1. app_users — Public platform users (Google OAuth)
CREATE TABLE app_users (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    avatar_url      TEXT,
    provider        VARCHAR(50)  NOT NULL DEFAULT 'GOOGLE',
    provider_user_id VARCHAR(255),
    user_status     VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,

    CONSTRAINT chk_app_users_status CHECK (user_status IN ('ACTIVE', 'LOCKED', 'DELETED'))
);

CREATE INDEX idx_app_users_email      ON app_users (email);
CREATE INDEX idx_app_users_status     ON app_users (user_status);
CREATE INDEX idx_app_users_created_at ON app_users (created_at);
CREATE INDEX idx_app_users_provider   ON app_users (provider);

-- 2. roles
CREATE TABLE roles (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  UNIQUE NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);

CREATE INDEX idx_roles_code ON roles (code);

-- 3. permissions
CREATE TABLE permissions (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(100)  UNIQUE NOT NULL,
    name        VARCHAR(150)  NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);

CREATE INDEX idx_permissions_code ON permissions (code);

-- 4. user_roles — Many-to-many: app_users <-> roles
CREATE TABLE user_roles (
    user_id    UUID        NOT NULL REFERENCES app_users (id),
    role_id    UUID        NOT NULL REFERENCES roles (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);

-- 5. role_permissions — Many-to-many: roles <-> permissions
CREATE TABLE role_permissions (
    role_id       UUID        NOT NULL REFERENCES roles (id),
    permission_id UUID        NOT NULL REFERENCES permissions (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_role_id       ON role_permissions (role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions (permission_id);

-- 6. internal_admin_accounts — Admin Portal accounts (separate from Google OAuth)
CREATE TABLE internal_admin_accounts (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) UNIQUE NOT NULL,
    full_name      VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    account_status VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ,

    CONSTRAINT chk_internal_admin_status CHECK (account_status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE INDEX idx_internal_admin_email      ON internal_admin_accounts (email);
CREATE INDEX idx_internal_admin_status     ON internal_admin_accounts (account_status);
CREATE INDEX idx_internal_admin_created_at ON internal_admin_accounts (created_at);

-- 7. internal_admin_roles — Many-to-many: internal_admin_accounts <-> roles
CREATE TABLE internal_admin_roles (
    admin_account_id UUID        NOT NULL REFERENCES internal_admin_accounts (id),
    role_id          UUID        NOT NULL REFERENCES roles (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (admin_account_id, role_id)
);

CREATE INDEX idx_internal_admin_roles_admin_id ON internal_admin_roles (admin_account_id);
CREATE INDEX idx_internal_admin_roles_role_id  ON internal_admin_roles (role_id);


-- ============================================================================
-- PART 2: SYSTEM SETTINGS + AUDIT
-- ============================================================================

-- 8. system_settings
CREATE TABLE system_settings (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    setting_key   VARCHAR(100) UNIQUE NOT NULL,
    setting_value TEXT         NOT NULL,
    value_type    VARCHAR(30)  NOT NULL,
    description   TEXT,
    is_editable   BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by    UUID         REFERENCES internal_admin_accounts (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ,

    CONSTRAINT chk_system_settings_value_type CHECK (value_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON'))
);

CREATE INDEX idx_system_settings_key ON system_settings (setting_key);

-- 9. audit_logs
CREATE TABLE audit_logs (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_type      VARCHAR(30)  NOT NULL,
    actor_user_id   UUID         REFERENCES app_users (id),
    actor_admin_id  UUID         REFERENCES internal_admin_accounts (id),
    actor_role_code VARCHAR(50),
    action          VARCHAR(100) NOT NULL,
    target_type     VARCHAR(100) NOT NULL,
    target_id       UUID,
    before_value    JSONB,
    after_value     JSONB,
    metadata        JSONB,
    ip_address      VARCHAR(100),
    user_agent      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_audit_logs_actor_type CHECK (actor_type IN ('USER', 'INTERNAL_ADMIN', 'SYSTEM'))
);

CREATE INDEX idx_audit_logs_actor_type     ON audit_logs (actor_type);
CREATE INDEX idx_audit_logs_actor_user_id  ON audit_logs (actor_user_id);
CREATE INDEX idx_audit_logs_actor_admin_id ON audit_logs (actor_admin_id);
CREATE INDEX idx_audit_logs_action         ON audit_logs (action);
CREATE INDEX idx_audit_logs_target_type    ON audit_logs (target_type);
CREATE INDEX idx_audit_logs_created_at     ON audit_logs (created_at);


-- ============================================================================
-- PART 3: PROFILE + TEACHER KYC
-- ============================================================================

-- 10. student_profiles
CREATE TABLE student_profiles (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         UNIQUE NOT NULL REFERENCES app_users (id),
    display_name VARCHAR(255),
    jlpt_goal    VARCHAR(20),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ
);

CREATE INDEX idx_student_profiles_user_id ON student_profiles (user_id);

-- 11. teacher_profiles
CREATE TABLE teacher_profiles (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         UNIQUE NOT NULL REFERENCES app_users (id),
    display_name       VARCHAR(255),
    bio                TEXT,
    kyc_status         VARCHAR(30)  NOT NULL DEFAULT 'NOT_SUBMITTED',
    can_publish_course BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ,

    CONSTRAINT chk_teacher_kyc_status CHECK (kyc_status IN ('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED', 'CORRECTION_REQUIRED'))
);

CREATE INDEX idx_teacher_profiles_user_id    ON teacher_profiles (user_id);
CREATE INDEX idx_teacher_profiles_kyc_status ON teacher_profiles (kyc_status);

-- 12. kyc_requests
CREATE TABLE kyc_requests (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id        UUID         NOT NULL REFERENCES teacher_profiles (id),
    status            VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    submitted_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    reviewed_by       UUID         REFERENCES internal_admin_accounts (id),
    reviewed_at       TIMESTAMPTZ,
    decision_reason   TEXT,
    ekyc_provider     VARCHAR(50),
    ekyc_reference_id VARCHAR(255),
    risk_level        VARCHAR(30),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ,

    CONSTRAINT chk_kyc_requests_status     CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CORRECTION_REQUIRED')),
    CONSTRAINT chk_kyc_requests_risk_level CHECK (risk_level IS NULL OR risk_level IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE INDEX idx_kyc_requests_teacher_id  ON kyc_requests (teacher_id);
CREATE INDEX idx_kyc_requests_status      ON kyc_requests (status);
CREATE INDEX idx_kyc_requests_reviewed_by ON kyc_requests (reviewed_by);
CREATE INDEX idx_kyc_requests_created_at  ON kyc_requests (created_at);

-- 13. kyc_documents
CREATE TABLE kyc_documents (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    kyc_request_id  UUID         NOT NULL REFERENCES kyc_requests (id),
    document_type   VARCHAR(50)  NOT NULL,
    file_url        TEXT         NOT NULL,
    file_name       VARCHAR(255),
    mime_type       VARCHAR(100),
    file_size       BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,

    CONSTRAINT chk_kyc_documents_type CHECK (document_type IN ('ID_CARD_FRONT', 'ID_CARD_BACK', 'SELFIE', 'CERTIFICATE', 'COPYRIGHT_AGREEMENT', 'OTHER'))
);

CREATE INDEX idx_kyc_documents_request_id ON kyc_documents (kyc_request_id);


-- ============================================================================
-- PART 4: COURSE + CONTENT + APPROVAL
-- ============================================================================

-- 14. courses
CREATE TABLE courses (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id       UUID           NOT NULL REFERENCES teacher_profiles (id),
    title            VARCHAR(255)   NOT NULL,
    slug             VARCHAR(255)   UNIQUE NOT NULL,
    description      TEXT,
    level_code       VARCHAR(20),
    price            NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency         VARCHAR(10)    NOT NULL DEFAULT 'VND',
    status           VARCHAR(30)    NOT NULL DEFAULT 'DRAFT',
    ai_supported     BOOLEAN        NOT NULL DEFAULT FALSE,
    submitted_at     TIMESTAMPTZ,
    approved_by      UUID           REFERENCES internal_admin_accounts (id),
    approved_at      TIMESTAMPTZ,
    rejection_reason TEXT,
    published_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ,

    CONSTRAINT chk_courses_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PUBLISHED', 'REJECTED', 'FORCED_DRAFT', 'ARCHIVED'))
);

CREATE INDEX idx_courses_teacher_id ON courses (teacher_id);
CREATE INDEX idx_courses_slug       ON courses (slug);
CREATE INDEX idx_courses_status     ON courses (status);
CREATE INDEX idx_courses_created_at ON courses (created_at);
CREATE INDEX idx_courses_level_code ON courses (level_code);

-- 15. course_modules
CREATE TABLE course_modules (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id   UUID         NOT NULL REFERENCES courses (id),
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    order_index INT          NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);

CREATE INDEX idx_course_modules_course_id ON course_modules (course_id);

-- 16. lessons
CREATE TABLE lessons (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id   UUID         NOT NULL REFERENCES course_modules (id),
    title       VARCHAR(255) NOT NULL,
    lesson_type VARCHAR(30)  NOT NULL,
    order_index INT          NOT NULL,
    is_preview  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,

    CONSTRAINT chk_lessons_type CHECK (lesson_type IN ('VIDEO', 'TEXT', 'QUIZ', 'FLASHCARD', 'WRITING', 'MIXED'))
);

CREATE INDEX idx_lessons_module_id ON lessons (module_id);

-- 17. lesson_blocks
CREATE TABLE lesson_blocks (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id   UUID        NOT NULL REFERENCES lessons (id),
    block_type  VARCHAR(30) NOT NULL,
    content     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    order_index INT         NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,

    CONSTRAINT chk_lesson_blocks_type CHECK (block_type IN ('TEXT', 'VIDEO', 'IMAGE', 'AUDIO', 'QUIZ', 'FLASHCARD', 'WRITING_PROMPT'))
);

CREATE INDEX idx_lesson_blocks_lesson_id ON lesson_blocks (lesson_id);

-- 18. course_approval_decisions
CREATE TABLE course_approval_decisions (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id  UUID         NOT NULL REFERENCES courses (id),
    decided_by UUID         NOT NULL REFERENCES internal_admin_accounts (id),
    decision   VARCHAR(30)  NOT NULL,
    reason     TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_course_approval_decision CHECK (decision IN ('APPROVED', 'REJECTED', 'CORRECTION_REQUIRED'))
);

CREATE INDEX idx_course_approval_course_id  ON course_approval_decisions (course_id);
CREATE INDEX idx_course_approval_decided_by ON course_approval_decisions (decided_by);
CREATE INDEX idx_course_approval_created_at ON course_approval_decisions (created_at);


-- ============================================================================
-- PART 5: ENROLLMENT + LEARNING
-- ============================================================================

-- 19. enrollments
CREATE TABLE enrollments (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id        UUID        NOT NULL REFERENCES student_profiles (id),
    course_id         UUID        NOT NULL REFERENCES courses (id),
    enrollment_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    enrolled_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMPTZ,

    CONSTRAINT uq_enrollments_student_course UNIQUE (student_id, course_id),
    CONSTRAINT chk_enrollments_status CHECK (enrollment_status IN ('ACTIVE', 'REFUNDED', 'REVOKED', 'COMPLETED'))
);

CREATE INDEX idx_enrollments_student_id ON enrollments (student_id);
CREATE INDEX idx_enrollments_course_id  ON enrollments (course_id);
CREATE INDEX idx_enrollments_status     ON enrollments (enrollment_status);

-- 20. lesson_progress
CREATE TABLE lesson_progress (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id    UUID           NOT NULL REFERENCES enrollments (id),
    lesson_id        UUID           NOT NULL REFERENCES lessons (id),
    status           VARCHAR(30)    NOT NULL DEFAULT 'NOT_STARTED',
    progress_percent NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ,

    CONSTRAINT uq_lesson_progress_enrollment_lesson UNIQUE (enrollment_id, lesson_id),
    CONSTRAINT chk_lesson_progress_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX idx_lesson_progress_enrollment_id ON lesson_progress (enrollment_id);
CREATE INDEX idx_lesson_progress_lesson_id     ON lesson_progress (lesson_id);
CREATE INDEX idx_lesson_progress_status        ON lesson_progress (status);


-- ============================================================================
-- PART 6: AI WRITING (SUGGESTION-ONLY — NOT grading / scoring / official assessment)
-- ============================================================================

-- 21. writing_submissions
CREATE TABLE writing_submissions (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID        NOT NULL REFERENCES enrollments (id),
    lesson_id     UUID        NOT NULL REFERENCES lessons (id),
    student_id    UUID        NOT NULL REFERENCES student_profiles (id),
    content       TEXT        NOT NULL,
    status        VARCHAR(40) NOT NULL DEFAULT 'SUBMITTED',
    submitted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ,

    CONSTRAINT chk_writing_submissions_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'SUGGESTION_PROCESSING', 'SUGGESTION_READY', 'SUGGESTION_FAILED', 'TEACHER_FEEDBACK_READY'))
);

CREATE INDEX idx_writing_submissions_enrollment_id ON writing_submissions (enrollment_id);
CREATE INDEX idx_writing_submissions_lesson_id     ON writing_submissions (lesson_id);
CREATE INDEX idx_writing_submissions_student_id    ON writing_submissions (student_id);
CREATE INDEX idx_writing_submissions_status        ON writing_submissions (status);
CREATE INDEX idx_writing_submissions_created_at    ON writing_submissions (created_at);

-- 22. ai_writing_suggestions
-- IMPORTANT: This table stores PRELIMINARY SUGGESTIONS ONLY.
-- AI Writing output is NOT grading, NOT scoring, NOT official assessment.
-- The is_official column is constrained to FALSE at the database level.
CREATE TABLE ai_writing_suggestions (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    writing_submission_id   UUID         NOT NULL REFERENCES writing_submissions (id),
    provider                VARCHAR(100),
    suggestion_status       VARCHAR(30)  NOT NULL DEFAULT 'READY',
    grammar_suggestions     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    vocabulary_suggestions  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    structure_suggestions   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    revision_guidance       TEXT,
    confidence_level        VARCHAR(30),
    is_official             BOOLEAN      NOT NULL DEFAULT FALSE,
    raw_response            JSONB,
    failure_reason          TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ai_suggestions_status     CHECK (suggestion_status IN ('READY', 'FAILED')),
    CONSTRAINT chk_ai_suggestions_confidence CHECK (confidence_level IS NULL OR confidence_level IN ('LOW', 'MEDIUM', 'HIGH')),
    -- CRITICAL: AI suggestions are NEVER official. Enforced at DB level.
    CONSTRAINT chk_ai_suggestions_not_official CHECK (is_official = FALSE)
);

CREATE INDEX idx_ai_writing_suggestions_submission_id ON ai_writing_suggestions (writing_submission_id);
CREATE INDEX idx_ai_writing_suggestions_created_at    ON ai_writing_suggestions (created_at);

-- 23. teacher_writing_feedback
-- Official teacher feedback, completely separate from AI suggestions.
-- The is_official column is constrained to TRUE at the database level.
CREATE TABLE teacher_writing_feedback (
    id                    UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    writing_submission_id UUID           NOT NULL REFERENCES writing_submissions (id),
    teacher_id            UUID           NOT NULL REFERENCES teacher_profiles (id),
    score                 NUMERIC(5, 2),
    comment               TEXT,
    rubric_result         JSONB,
    is_official           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ,

    -- CRITICAL: Teacher feedback is ALWAYS official. Enforced at DB level.
    CONSTRAINT chk_teacher_feedback_is_official CHECK (is_official = TRUE)
);

CREATE INDEX idx_teacher_feedback_submission_id ON teacher_writing_feedback (writing_submission_id);
CREATE INDEX idx_teacher_feedback_teacher_id    ON teacher_writing_feedback (teacher_id);
CREATE INDEX idx_teacher_feedback_created_at    ON teacher_writing_feedback (created_at);

-- 24. ai_usage_logs
CREATE TABLE ai_usage_logs (
    id                    UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID           REFERENCES app_users (id),
    course_id             UUID           REFERENCES courses (id),
    writing_submission_id UUID           REFERENCES writing_submissions (id),
    feature_code          VARCHAR(50)    NOT NULL,
    provider              VARCHAR(100),
    request_status        VARCHAR(30)    NOT NULL,
    input_tokens          INT,
    output_tokens         INT,
    estimated_cost        NUMERIC(12, 4),
    failure_reason        TEXT,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ai_usage_feature_code   CHECK (feature_code IN ('AI_CHATBOT', 'AI_WRITING_ASSISTANCE')),
    CONSTRAINT chk_ai_usage_request_status CHECK (request_status IN ('SUCCESS', 'FAILED', 'BLOCKED'))
);

CREATE INDEX idx_ai_usage_logs_user_id      ON ai_usage_logs (user_id);
CREATE INDEX idx_ai_usage_logs_course_id    ON ai_usage_logs (course_id);
CREATE INDEX idx_ai_usage_logs_feature_code ON ai_usage_logs (feature_code);
CREATE INDEX idx_ai_usage_logs_created_at   ON ai_usage_logs (created_at);


-- ============================================================================
-- PART 7: COMMERCE + WALLET + REFUND + PAYOUT
-- ============================================================================

-- 25. orders
CREATE TABLE orders (
    id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id   UUID           NOT NULL REFERENCES student_profiles (id),
    order_code   VARCHAR(50)    UNIQUE NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    currency     VARCHAR(10)    NOT NULL DEFAULT 'VND',
    order_status VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ,

    CONSTRAINT chk_orders_status CHECK (order_status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED', 'CANCELLED'))
);

CREATE INDEX idx_orders_student_id  ON orders (student_id);
CREATE INDEX idx_orders_order_code  ON orders (order_code);
CREATE INDEX idx_orders_status      ON orders (order_status);
CREATE INDEX idx_orders_created_at  ON orders (created_at);

-- 26. order_items
CREATE TABLE order_items (
    id         UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id   UUID           NOT NULL REFERENCES orders (id),
    course_id  UUID           NOT NULL REFERENCES courses (id),
    price      NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_items_order_id  ON order_items (order_id);
CREATE INDEX idx_order_items_course_id ON order_items (course_id);

-- 27. payment_transactions
CREATE TABLE payment_transactions (
    id                      UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                UUID           NOT NULL REFERENCES orders (id),
    provider                VARCHAR(50)    NOT NULL,
    provider_transaction_id VARCHAR(255),
    amount                  NUMERIC(12, 2) NOT NULL,
    status                  VARCHAR(30)    NOT NULL,
    raw_response            JSONB,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ,

    CONSTRAINT chk_payment_transactions_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'))
);

CREATE INDEX idx_payment_transactions_order_id   ON payment_transactions (order_id);
CREATE INDEX idx_payment_transactions_status     ON payment_transactions (status);
CREATE INDEX idx_payment_transactions_created_at ON payment_transactions (created_at);

-- 28. wallets
CREATE TABLE wallets (
    id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_type     VARCHAR(30)    NOT NULL,
    student_id     UUID           REFERENCES student_profiles (id),
    teacher_id     UUID           REFERENCES teacher_profiles (id),
    balance        NUMERIC(12, 2) NOT NULL DEFAULT 0,
    frozen_balance NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency       VARCHAR(10)    NOT NULL DEFAULT 'VND',
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ,

    CONSTRAINT chk_wallets_owner_type CHECK (owner_type IN ('STUDENT', 'TEACHER', 'PLATFORM'))
);

CREATE INDEX idx_wallets_owner_type ON wallets (owner_type);
CREATE INDEX idx_wallets_student_id ON wallets (student_id);
CREATE INDEX idx_wallets_teacher_id ON wallets (teacher_id);

-- 29. wallet_transactions
CREATE TABLE wallet_transactions (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id        UUID           NOT NULL REFERENCES wallets (id),
    transaction_type VARCHAR(50)    NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL,
    direction        VARCHAR(10)    NOT NULL,
    reference_type   VARCHAR(50),
    reference_id     UUID,
    note             TEXT,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_wallet_tx_type      CHECK (transaction_type IN ('PURCHASE', 'REFUND', 'REVENUE_SHARE', 'PAYOUT', 'ADJUSTMENT', 'ESCROW_HOLD', 'ESCROW_RELEASE')),
    CONSTRAINT chk_wallet_tx_direction CHECK (direction IN ('IN', 'OUT'))
);

CREATE INDEX idx_wallet_transactions_wallet_id  ON wallet_transactions (wallet_id);
CREATE INDEX idx_wallet_transactions_type       ON wallet_transactions (transaction_type);
CREATE INDEX idx_wallet_transactions_created_at ON wallet_transactions (created_at);

-- 30. escrow_ledger
CREATE TABLE escrow_ledger (
    id         UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id   UUID           NOT NULL REFERENCES orders (id),
    course_id  UUID           NOT NULL REFERENCES courses (id),
    teacher_id UUID           NOT NULL REFERENCES teacher_profiles (id),
    amount     NUMERIC(12, 2) NOT NULL,
    status     VARCHAR(30)    NOT NULL DEFAULT 'HELD',
    release_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,

    CONSTRAINT chk_escrow_ledger_status CHECK (status IN ('HELD', 'RELEASED', 'REFUNDED', 'FROZEN'))
);

CREATE INDEX idx_escrow_ledger_order_id   ON escrow_ledger (order_id);
CREATE INDEX idx_escrow_ledger_course_id  ON escrow_ledger (course_id);
CREATE INDEX idx_escrow_ledger_teacher_id ON escrow_ledger (teacher_id);
CREATE INDEX idx_escrow_ledger_status     ON escrow_ledger (status);
CREATE INDEX idx_escrow_ledger_created_at ON escrow_ledger (created_at);

-- 31. refund_requests
CREATE TABLE refund_requests (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id              UUID        NOT NULL REFERENCES orders (id),
    student_id            UUID        NOT NULL REFERENCES student_profiles (id),
    status                VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reason                TEXT        NOT NULL,
    eligibility_snapshot  JSONB,
    decided_by            UUID        REFERENCES internal_admin_accounts (id),
    decision_note         TEXT,
    decided_at            TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ,

    CONSTRAINT chk_refund_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX idx_refund_requests_order_id   ON refund_requests (order_id);
CREATE INDEX idx_refund_requests_student_id ON refund_requests (student_id);
CREATE INDEX idx_refund_requests_status     ON refund_requests (status);
CREATE INDEX idx_refund_requests_created_at ON refund_requests (created_at);

-- 32. withdrawal_requests
CREATE TABLE withdrawal_requests (
    id                   UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id           UUID           NOT NULL REFERENCES teacher_profiles (id),
    amount               NUMERIC(12, 2) NOT NULL,
    status               VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    bank_account_snapshot JSONB,
    requested_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    decided_by           UUID           REFERENCES internal_admin_accounts (id),
    decision_note        TEXT,
    decided_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ,

    CONSTRAINT chk_withdrawal_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXECUTED', 'FAILED'))
);

CREATE INDEX idx_withdrawal_requests_teacher_id ON withdrawal_requests (teacher_id);
CREATE INDEX idx_withdrawal_requests_status     ON withdrawal_requests (status);
CREATE INDEX idx_withdrawal_requests_created_at ON withdrawal_requests (created_at);

-- 33. payout_settlements
CREATE TABLE payout_settlements (
    id                      UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    withdrawal_request_id   UUID           NOT NULL REFERENCES withdrawal_requests (id),
    executed_by             UUID           REFERENCES internal_admin_accounts (id),
    provider                VARCHAR(50),
    provider_reference_id   VARCHAR(255),
    amount                  NUMERIC(12, 2) NOT NULL,
    status                  VARCHAR(30)    NOT NULL,
    reconciliation_status   VARCHAR(30),
    raw_response            JSONB,
    executed_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ,

    CONSTRAINT chk_payout_settlements_status  CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'RECONCILIATION_MISMATCH')),
    CONSTRAINT chk_payout_reconciliation      CHECK (reconciliation_status IS NULL OR reconciliation_status IN ('NOT_CHECKED', 'MATCHED', 'MISMATCHED'))
);

CREATE INDEX idx_payout_settlements_withdrawal_id ON payout_settlements (withdrawal_request_id);
CREATE INDEX idx_payout_settlements_status        ON payout_settlements (status);
CREATE INDEX idx_payout_settlements_created_at    ON payout_settlements (created_at);


-- ============================================================================
-- PART 8: MODERATION + NOTIFICATION
-- ============================================================================

-- 34. violation_reports
CREATE TABLE violation_reports (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_user_id UUID        NOT NULL REFERENCES app_users (id),
    target_type      VARCHAR(50) NOT NULL,
    target_id        UUID        NOT NULL,
    reason           TEXT        NOT NULL,
    status           VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ,

    CONSTRAINT chk_violation_reports_target_type CHECK (target_type IN ('COURSE', 'LESSON', 'REVIEW', 'USER')),
    CONSTRAINT chk_violation_reports_status      CHECK (status IN ('PENDING', 'RESOLVED', 'REJECTED'))
);

CREATE INDEX idx_violation_reports_reporter   ON violation_reports (reporter_user_id);
CREATE INDEX idx_violation_reports_target     ON violation_reports (target_type, target_id);
CREATE INDEX idx_violation_reports_status     ON violation_reports (status);
CREATE INDEX idx_violation_reports_created_at ON violation_reports (created_at);

-- 35. moderation_decisions
CREATE TABLE moderation_decisions (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    violation_report_id UUID        NOT NULL REFERENCES violation_reports (id),
    decided_by          UUID        NOT NULL REFERENCES internal_admin_accounts (id),
    decision            VARCHAR(50) NOT NULL,
    reason              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_moderation_decision CHECK (decision IN ('NO_VIOLATION', 'REQUEST_CORRECTION', 'FORCE_DRAFT', 'REMOVE_CONTENT', 'BAN', 'DISMISS'))
);

CREATE INDEX idx_moderation_decisions_report_id  ON moderation_decisions (violation_report_id);
CREATE INDEX idx_moderation_decisions_decided_by ON moderation_decisions (decided_by);
CREATE INDEX idx_moderation_decisions_created_at ON moderation_decisions (created_at);

-- 36. notifications
CREATE TABLE notifications (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id  UUID         REFERENCES app_users (id),
    recipient_admin_id UUID         REFERENCES internal_admin_accounts (id),
    title              VARCHAR(255) NOT NULL,
    message            TEXT         NOT NULL,
    notification_type  VARCHAR(50),
    is_read            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    read_at            TIMESTAMPTZ
);

CREATE INDEX idx_notifications_recipient_user  ON notifications (recipient_user_id);
CREATE INDEX idx_notifications_recipient_admin ON notifications (recipient_admin_id);
CREATE INDEX idx_notifications_is_read         ON notifications (is_read);
CREATE INDEX idx_notifications_created_at      ON notifications (created_at);
