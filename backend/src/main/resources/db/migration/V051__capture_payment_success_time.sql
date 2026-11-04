ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS succeeded_at TIMESTAMPTZ;

UPDATE payment_transactions
SET succeeded_at = COALESCE(updated_at, created_at)
WHERE succeeded_at IS NULL
  AND status IN ('SUCCESS', 'REFUNDED');

CREATE INDEX IF NOT EXISTS idx_payment_transactions_order_succeeded_at
    ON payment_transactions (order_id, succeeded_at DESC)
    WHERE succeeded_at IS NOT NULL;
