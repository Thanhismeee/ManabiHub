-- Finance expense hardening:
-- 1. keep the supporting evidence needed for foreign-currency invoices;
-- 2. expose a due date for operational follow-up;
-- 3. enforce duplicate prevention at the database boundary so concurrent
--    requests cannot create the same active provider invoice twice.

ALTER TABLE system_expenses
    ADD COLUMN IF NOT EXISTS due_date DATE,
    ADD COLUMN IF NOT EXISTS exchange_rate_date DATE,
    ADD COLUMN IF NOT EXISTS exchange_rate_source VARCHAR(120);

ALTER TABLE system_expenses
    DROP CONSTRAINT IF EXISTS chk_system_expense_due_date;

ALTER TABLE system_expenses
    ADD CONSTRAINT chk_system_expense_due_date
        CHECK (due_date IS NULL OR due_date >= incurred_at);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM system_expenses
        WHERE status <> 'VOID'
          AND NULLIF(BTRIM(provider_code), '') IS NOT NULL
          AND NULLIF(BTRIM(invoice_number), '') IS NOT NULL
        GROUP BY LOWER(BTRIM(provider_code)), LOWER(BTRIM(invoice_number)), incurred_at
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce finance invoice uniqueness: duplicate active provider/invoice/date records exist';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_system_expenses_active_provider_invoice_date
    ON system_expenses (
        LOWER(BTRIM(provider_code)),
        LOWER(BTRIM(invoice_number)),
        incurred_at
    )
    WHERE status <> 'VOID'
      AND NULLIF(BTRIM(provider_code), '') IS NOT NULL
      AND NULLIF(BTRIM(invoice_number), '') IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_system_expenses_due_status
    ON system_expenses(due_date, status)
    WHERE due_date IS NOT NULL AND status NOT IN ('PAID', 'VOID');
