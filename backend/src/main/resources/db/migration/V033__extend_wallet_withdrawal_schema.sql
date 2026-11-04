-- V033: Extend wallet and withdrawal schema for payout feature
-- Add new transaction types and withdrawal statuses needed by withdrawal module

-- 1. Drop old CHECK constraint on wallet_transactions.transaction_type and add extended one
ALTER TABLE wallet_transactions DROP CONSTRAINT IF EXISTS chk_wallet_tx_type;
ALTER TABLE wallet_transactions ADD CONSTRAINT chk_wallet_tx_type
    CHECK (transaction_type IN (
        'PURCHASE', 'REFUND', 'REVENUE_SHARE', 'PAYOUT', 'ADJUSTMENT',
        'ESCROW_HOLD', 'ESCROW_RELEASE',
        'REVENUE_CREDITED', 'REVENUE_CLEARED',
        'WITHDRAWAL_RESERVATION', 'WITHDRAWAL_COMPLETED',
        'WITHDRAWAL_REJECTED', 'WITHDRAWAL_CANCELLED',
        'ADMIN_ADJUSTMENT'
    ));

-- 2. Drop old CHECK constraint on withdrawal_requests.status and add extended one (add CANCELLED)
ALTER TABLE withdrawal_requests DROP CONSTRAINT IF EXISTS chk_withdrawal_requests_status;
ALTER TABLE withdrawal_requests ADD CONSTRAINT chk_withdrawal_requests_status
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXECUTED', 'FAILED', 'CANCELLED'));

-- 3. Add unique constraint on wallets.teacher_id if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_wallets_teacher_id'
    ) THEN
        ALTER TABLE wallets ADD CONSTRAINT uq_wallets_teacher_id UNIQUE (teacher_id);
    END IF;
END$$;
