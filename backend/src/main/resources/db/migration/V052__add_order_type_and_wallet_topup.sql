-- MHB-37 / Wallet top-up payment.
-- 1) Distinguish a course purchase from a wallet top-up on the orders table.
-- 2) Allow TOP_UP wallet ledger entries.
-- 3) One money wallet per student (mirrors the teacher_id uniqueness from V033).

ALTER TABLE orders ADD COLUMN IF NOT EXISTS order_type VARCHAR(20) NOT NULL DEFAULT 'COURSE';

ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_type;
ALTER TABLE orders ADD CONSTRAINT chk_orders_type
    CHECK (order_type IN ('COURSE', 'WALLET_TOPUP'));

ALTER TABLE wallet_transactions DROP CONSTRAINT IF EXISTS chk_wallet_tx_type;
ALTER TABLE wallet_transactions ADD CONSTRAINT chk_wallet_tx_type
    CHECK (transaction_type IN (
        'PURCHASE', 'REFUND', 'REVENUE_SHARE', 'PAYOUT', 'ADJUSTMENT',
        'ESCROW_HOLD', 'ESCROW_RELEASE',
        'REVENUE_CREDITED', 'REVENUE_CLEARED',
        'WITHDRAWAL_RESERVATION', 'WITHDRAWAL_COMPLETED',
        'WITHDRAWAL_REJECTED', 'WITHDRAWAL_CANCELLED',
        'ADMIN_ADJUSTMENT', 'TOP_UP'
    ));

CREATE UNIQUE INDEX IF NOT EXISTS uq_wallets_student_id
    ON wallets (student_id) WHERE student_id IS NOT NULL;
