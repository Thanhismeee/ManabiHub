-- Financial hardening follow-up for MHB-37.
-- - one durable reservation per order for wallet-funded purchases
-- - idempotent wallet ledger entries
-- - explicit wallet-refund settlement evidence
-- - payment composition/backfill for wallet and combined payments

ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS chk_orders_wallet_amount;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_wallet_amount
    CHECK (
        total_amount >= 0
        AND wallet_amount >= 0
        AND wallet_amount <= total_amount
    );

ALTER TABLE wallets
    DROP CONSTRAINT IF EXISTS chk_wallets_owner_reference;

ALTER TABLE wallets
    ADD CONSTRAINT chk_wallets_owner_reference
    CHECK (
        (owner_type = 'STUDENT' AND student_id IS NOT NULL AND teacher_id IS NULL)
        OR (owner_type = 'TEACHER' AND teacher_id IS NOT NULL AND student_id IS NULL)
        OR (owner_type = 'PLATFORM' AND student_id IS NULL AND teacher_id IS NULL)
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_wallets_platform_singleton
    ON wallets (owner_type)
    WHERE owner_type = 'PLATFORM';

ALTER TABLE wallet_transactions
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(160);

CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_transactions_idempotency_key
    ON wallet_transactions (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE wallet_payment_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(40) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    captured_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,

    CONSTRAINT chk_wallet_payment_reservation_amount CHECK (amount > 0),
    CONSTRAINT chk_wallet_payment_reservation_status CHECK (
        status IN ('RESERVED', 'CAPTURED', 'RELEASED', 'RECONCILIATION_REQUIRED')
    )
);

CREATE INDEX idx_wallet_payment_reservations_expiry
    ON wallet_payment_reservations (expires_at)
    WHERE status = 'RESERVED';

-- Existing full-wallet payments were created after V051 and therefore may not
-- have succeeded_at. Backfill them before refund eligibility reads the value.
UPDATE payment_transactions
SET succeeded_at = COALESCE(updated_at, created_at)
WHERE provider = 'WALLET'
  AND status IN ('SUCCESS', 'REFUNDED')
  AND succeeded_at IS NULL;

-- Record the wallet share on legacy full-wallet orders.
UPDATE orders purchase_order
SET wallet_amount = purchase_order.total_amount
WHERE purchase_order.order_type = 'COURSE'
  AND purchase_order.order_status IN ('PAID', 'REFUNDED')
  AND purchase_order.wallet_amount = 0
  AND EXISTS (
      SELECT 1
      FROM payment_transactions payment
      WHERE payment.order_id = purchase_order.id
        AND payment.provider = 'WALLET'
        AND payment.status IN ('SUCCESS', 'REFUNDED')
  );

-- Legacy combined payments stored only the gateway component. Reconstruct the
-- wallet component from the immutable purchase ledger so composition sums to
-- the order total for refund validation and financial audit.
INSERT INTO payment_transactions (
    id,
    order_id,
    provider,
    provider_transaction_id,
    amount,
    status,
    succeeded_at,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    purchase_order.id,
    'WALLET',
    wallet_tx.id::text,
    purchase_order.wallet_amount,
    CASE
        WHEN purchase_order.order_status = 'REFUNDED' THEN 'REFUNDED'
        ELSE 'SUCCESS'
    END,
    COALESCE(gateway_payment.succeeded_at, purchase_order.updated_at, purchase_order.created_at),
    COALESCE(wallet_tx.created_at, purchase_order.updated_at, purchase_order.created_at),
    purchase_order.updated_at
FROM orders purchase_order
JOIN wallet_transactions wallet_tx
  ON wallet_tx.reference_type = 'ORDER'
 AND wallet_tx.reference_id = purchase_order.id
 AND wallet_tx.transaction_type = 'PURCHASE'
LEFT JOIN LATERAL (
    SELECT payment.succeeded_at
    FROM payment_transactions payment
    WHERE payment.order_id = purchase_order.id
      AND payment.provider <> 'WALLET'
      AND payment.status IN ('SUCCESS', 'REFUNDED')
    ORDER BY payment.succeeded_at DESC NULLS LAST, payment.created_at DESC
    LIMIT 1
) gateway_payment ON TRUE
WHERE purchase_order.order_type = 'COURSE'
  AND purchase_order.order_status IN ('PAID', 'REFUNDED')
  AND purchase_order.wallet_amount > 0
  AND purchase_order.wallet_amount < purchase_order.total_amount
  AND NOT EXISTS (
      SELECT 1
      FROM payment_transactions existing
      WHERE existing.order_id = purchase_order.id
        AND existing.provider = 'WALLET'
        AND existing.status IN ('SUCCESS', 'REFUNDED')
  )
ON CONFLICT (provider, provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL
    DO NOTHING;

ALTER TABLE refund_requests
    ADD COLUMN IF NOT EXISTS settlement_method VARCHAR(30),
    ADD COLUMN IF NOT EXISTS settlement_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS settled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS wallet_transaction_id UUID REFERENCES wallet_transactions(id);

ALTER TABLE refund_requests
    DROP CONSTRAINT IF EXISTS chk_refund_settlement_method,
    DROP CONSTRAINT IF EXISTS chk_refund_settlement_status;

ALTER TABLE refund_requests
    ADD CONSTRAINT chk_refund_settlement_method
        CHECK (settlement_method IS NULL OR settlement_method IN ('WALLET')),
    ADD CONSTRAINT chk_refund_settlement_status
        CHECK (settlement_status IS NULL OR settlement_status IN ('PENDING', 'COMPLETED', 'FAILED'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_requests_wallet_transaction
    ON refund_requests (wallet_transaction_id)
    WHERE wallet_transaction_id IS NOT NULL;
