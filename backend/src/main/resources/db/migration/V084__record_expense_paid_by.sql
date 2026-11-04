-- MHB-006: retain the Finance Manager who completed the CONFIRMED -> PAID transition
-- directly on the expense record, in addition to the immutable audit event.
ALTER TABLE system_expenses
    ADD COLUMN IF NOT EXISTS paid_by UUID REFERENCES internal_admin_accounts(id);
