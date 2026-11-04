-- MHB-66: finance operations, actual expense documents, and non-blocking
-- SYSTEM_ADMIN oversight. Operational decisions remain owned by COURSE_MANAGER
-- and FINANCE_MANAGER.

INSERT INTO permissions (id, code, name, description) VALUES
    ('b0000000-0000-0000-0000-00000000000e', 'FINANCE_REVENUE_VIEW', 'View Platform Revenue', 'View system revenue, refund, fee, and expense summaries'),
    ('b0000000-0000-0000-0000-00000000000f', 'FINANCE_EXPENSE_VIEW', 'View System Expenses', 'View actual system expense documents and line items'),
    ('b0000000-0000-0000-0000-000000000010', 'FINANCE_EXPENSE_MANAGE', 'Manage System Expenses', 'Create, confirm, pay, and void actual system expense documents'),
    ('b0000000-0000-0000-0000-000000000011', 'OPERATIONAL_DECISION_REVIEW_VIEW', 'Review Operational Decisions', 'View Course Manager and Finance Manager decision evidence'),
    ('b0000000-0000-0000-0000-000000000012', 'OPERATIONAL_DECISION_WARNING_SEND', 'Warn Decision Owner', 'Send a non-blocking warning about an operational decision')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission
  ON permission.code IN (
      'FINANCE_REVENUE_VIEW',
      'FINANCE_EXPENSE_VIEW',
      'FINANCE_EXPENSE_MANAGE'
  )
WHERE role.code = 'FINANCE_MANAGER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission
  ON permission.code IN (
      'OPERATIONAL_DECISION_REVIEW_VIEW',
      'OPERATIONAL_DECISION_WARNING_SEND'
  )
WHERE role.code = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;

-- SYSTEM_ADMIN keeps configuration, account, audit, and oversight permissions,
-- but must not execute operational decisions owned by the two manager roles.
DELETE FROM role_permissions mapping
USING roles role, permissions permission
WHERE mapping.role_id = role.id
  AND mapping.permission_id = permission.id
  AND role.code = 'SYSTEM_ADMIN'
  AND permission.code IN (
      'KYC_REVIEW',
      'COURSE_REVIEW',
      'VIOLATION_RESOLVE',
      'VIOLATION_CONTENT_ENFORCE',
      'VIOLATION_SEVERE_ENFORCE',
      'REFUND_REVIEW',
      'PAYOUT_EXECUTE',
      'FINANCE_EVIDENCE_VIEW'
  );

CREATE TABLE IF NOT EXISTS system_expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_code VARCHAR(50) NOT NULL UNIQUE,
    vendor_name VARCHAR(255) NOT NULL,
    provider_code VARCHAR(80),
    invoice_number VARCHAR(120),
    description TEXT,
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    exchange_rate NUMERIC(18, 6) NOT NULL DEFAULT 1,
    original_total NUMERIC(18, 2) NOT NULL,
    total_amount_vnd NUMERIC(18, 2) NOT NULL,
    incurred_at DATE NOT NULL,
    billing_period_from DATE,
    billing_period_to DATE,
    paid_at TIMESTAMPTZ,
    evidence_reference VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    source_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL_INVOICE',
    created_by UUID NOT NULL REFERENCES internal_admin_accounts(id),
    confirmed_by UUID REFERENCES internal_admin_accounts(id),
    confirmed_at TIMESTAMPTZ,
    voided_by UUID REFERENCES internal_admin_accounts(id),
    voided_at TIMESTAMPTZ,
    void_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_system_expense_status
        CHECK (status IN ('DRAFT', 'CONFIRMED', 'PAID', 'VOID')),
    CONSTRAINT chk_system_expense_source
        CHECK (source_type IN ('MANUAL_INVOICE', 'IMPORTED_INVOICE', 'ADJUSTMENT')),
    CONSTRAINT chk_system_expense_amounts
        CHECK (original_total > 0 AND total_amount_vnd > 0 AND exchange_rate > 0),
    CONSTRAINT chk_system_expense_billing_period
        CHECK (billing_period_from IS NULL OR billing_period_to IS NULL OR billing_period_from <= billing_period_to),
    CONSTRAINT chk_system_expense_confirmation
        CHECK (
            (status = 'DRAFT' AND confirmed_by IS NULL AND confirmed_at IS NULL)
            OR (status IN ('CONFIRMED', 'PAID', 'VOID'))
        ),
    CONSTRAINT chk_system_expense_void
        CHECK (
            status <> 'VOID'
            OR (voided_by IS NOT NULL AND voided_at IS NOT NULL AND BTRIM(COALESCE(void_reason, '')) <> '')
        )
);

CREATE TABLE IF NOT EXISTS system_expense_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_id UUID NOT NULL REFERENCES system_expenses(id) ON DELETE CASCADE,
    category_code VARCHAR(50) NOT NULL,
    description VARCHAR(500) NOT NULL,
    original_amount NUMERIC(18, 2) NOT NULL,
    amount_vnd NUMERIC(18, 2) NOT NULL,
    line_order INT NOT NULL,

    CONSTRAINT chk_system_expense_line_category CHECK (category_code IN (
        'INFRA_APP_COMPUTE', 'INFRA_APP_DISK', 'INFRA_DATABASE',
        'INFRA_FRONTEND_HOSTING', 'INFRA_API_GATEWAY', 'INFRA_OBJECT_STORAGE',
        'INFRA_CDN', 'INFRA_NETWORK', 'INFRA_MONITORING', 'INFRA_BACKUP_DR',
        'SMS_OTP', 'AI_CHAT', 'AI_WRITING', 'KYC_IDENTITY', 'KYC_CERTIFICATE',
        'EMAIL_TRANSACTIONAL', 'BANK_ACCOUNT_VERIFY', 'EXTERNAL_STORAGE_SCAN',
        'PAYMENT_GATEWAY_FEE', 'PAYMENT_REFUND_FEE', 'PAYMENT_CHARGEBACK_FEE',
        'PAYOUT_TRANSFER_FEE', 'PAYOUT_RECONCILIATION_FEE', 'CURRENCY_CONVERSION_FEE',
        'PROMOTION_GAME_REWARD', 'PROMOTION_ATTENDANCE_REWARD',
        'CUSTOMER_COMPENSATION', 'PROMOTION_OTHER',
        'DOMAIN_DNS', 'TLS_CERTIFICATE', 'SECRET_MANAGEMENT', 'SECURITY_WAF',
        'SECURITY_SCANNING', 'OBSERVABILITY_TOOL', 'CI_CD', 'SOURCE_CONTROL',
        'BACKUP_TOOL', 'PERSONNEL_ENGINEERING', 'PERSONNEL_CONTENT',
        'PERSONNEL_FINANCE', 'PERSONNEL_SUPPORT', 'CONTENT_PRODUCTION',
        'MARKETING_ADS', 'SALES_PARTNERSHIP', 'LEGAL_COMPLIANCE',
        'ACCOUNTING_AUDIT', 'OFFICE_EQUIPMENT', 'TRAINING_RECRUITMENT',
        'CUSTOMER_SUPPORT_TOOL', 'DESIGN_COLLABORATION_TOOL', 'OTHER_OPERATIONAL'
    )),
    CONSTRAINT chk_system_expense_line_amounts
        CHECK (original_amount > 0 AND amount_vnd > 0),
    CONSTRAINT uq_system_expense_line_order UNIQUE (expense_id, line_order)
);

CREATE INDEX IF NOT EXISTS idx_system_expenses_status_incurred
    ON system_expenses(status, incurred_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_system_expenses_vendor
    ON system_expenses(LOWER(vendor_name));
CREATE INDEX IF NOT EXISTS idx_system_expense_lines_category
    ON system_expense_lines(category_code, expense_id);

CREATE TABLE IF NOT EXISTS operational_decision_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_log_id UUID NOT NULL UNIQUE REFERENCES audit_logs(id),
    review_status VARCHAR(20) NOT NULL,
    warning_level VARCHAR(20),
    review_note TEXT,
    reviewed_by UUID NOT NULL REFERENCES internal_admin_accounts(id),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    warning_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_operational_review_status
        CHECK (review_status IN ('REVIEWED', 'WARNING_SENT')),
    CONSTRAINT chk_operational_warning_level
        CHECK (warning_level IS NULL OR warning_level IN ('INFO', 'WARNING', 'HIGH')),
    CONSTRAINT chk_operational_warning_payload
        CHECK (
            (review_status = 'REVIEWED' AND warning_level IS NULL AND warning_sent_at IS NULL)
            OR (review_status = 'WARNING_SENT'
                AND warning_level IS NOT NULL
                AND warning_sent_at IS NOT NULL
                AND BTRIM(COALESCE(review_note, '')) <> '')
        )
);

CREATE INDEX IF NOT EXISTS idx_operational_decision_reviews_status
    ON operational_decision_reviews(review_status, reviewed_at DESC);
