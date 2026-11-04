-- V048: provider-confirmed, item-scoped and retry-safe refund execution.
--
-- A Finance decision is not proof that the payment provider returned money.
-- Keep provider attempts separate, use one stable idempotency key per refund
-- request, and mark an order/payment REFUNDED only after every order item was
-- confirmed and reversed.

ALTER TABLE refund_requests
    DROP CONSTRAINT IF EXISTS chk_refund_requests_status;

ALTER TABLE refund_requests
    ADD COLUMN order_item_id UUID REFERENCES order_items(id),
    ADD COLUMN decision_reason_code VARCHAR(60),
    ADD COLUMN reconciliation_reason_code VARCHAR(80),
    ADD COLUMN provider_status VARCHAR(40) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN provider_attempt_count INT NOT NULL DEFAULT 0;

ALTER TABLE refund_requests
    ADD CONSTRAINT chk_refund_requests_status
        CHECK (status IN (
            'PENDING',
            'PROCESSING',
            'RECONCILIATION_REQUIRED',
            'APPROVED',
            'REJECTED',
            'CANCELLED'
        )),
    ADD CONSTRAINT chk_refund_provider_status
        CHECK (provider_status IN (
            'NOT_REQUESTED',
            'PROCESSING',
            'SUCCESS',
            'FAILED',
            'PENDING',
            'UNAVAILABLE',
            'INVALID_RESULT'
        )),
    ADD CONSTRAINT chk_refund_provider_attempt_count
        CHECK (provider_attempt_count >= 0);

-- Existing orders are currently single-course purchases. Backfill only when
-- the relationship is unambiguous; never guess for legacy multi-item data.
UPDATE refund_requests request
SET order_item_id = (
    SELECT item.id
    FROM order_items item
    WHERE item.order_id = request.order_id
    LIMIT 1
)
WHERE request.order_item_id IS NULL
  AND (
      SELECT COUNT(*)
      FROM order_items item
      WHERE item.order_id = request.order_id
  ) = 1;

UPDATE refund_requests
SET status = 'RECONCILIATION_REQUIRED',
    reconciliation_reason_code = 'LEGACY_AMBIGUOUS_ORDER_ITEM'
WHERE order_item_id IS NULL
  AND status IN ('PENDING', 'APPROVED');

CREATE INDEX idx_refund_requests_order_item
    ON refund_requests(order_item_id);

-- Older application versions did not prevent duplicate active requests.
-- Preserve the highest-value/oldest record and quarantine every duplicate so
-- the new uniqueness guarantee can be installed without guessing twice.
WITH ranked_active_requests AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY order_item_id
               ORDER BY CASE status
                            WHEN 'APPROVED' THEN 0
                            WHEN 'RECONCILIATION_REQUIRED' THEN 1
                            WHEN 'PROCESSING' THEN 2
                            ELSE 3
                        END,
                        created_at,
                        id
           ) AS active_rank
    FROM refund_requests
    WHERE order_item_id IS NOT NULL
      AND status IN (
          'PENDING',
          'PROCESSING',
          'RECONCILIATION_REQUIRED',
          'APPROVED'
      )
)
UPDATE refund_requests request
SET status = 'CANCELLED',
    reconciliation_reason_code = 'LEGACY_DUPLICATE_ACTIVE_REQUEST'
FROM ranked_active_requests ranked
WHERE request.id = ranked.id
  AND ranked.active_rank > 1;

CREATE UNIQUE INDEX uq_refund_request_active_order_item
    ON refund_requests(order_item_id)
    WHERE order_item_id IS NOT NULL
      AND status IN (
          'PENDING',
          'PROCESSING',
          'RECONCILIATION_REQUIRED',
          'APPROVED'
      );

CREATE TABLE refund_provider_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    refund_request_id UUID NOT NULL UNIQUE REFERENCES refund_requests(id),
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    provider VARCHAR(50) NOT NULL,
    provider_request_id VARCHAR(160) NOT NULL UNIQUE,
    provider_reference VARCHAR(255),
    requested_amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    status VARCHAR(40) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 1,
    result_code VARCHAR(100),
    result_message VARCHAR(500),
    result_authenticated BOOLEAN NOT NULL DEFAULT FALSE,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,

    CONSTRAINT chk_refund_attempt_amount CHECK (requested_amount > 0),
    CONSTRAINT chk_refund_attempt_count CHECK (attempt_count > 0),
    CONSTRAINT chk_refund_attempt_status CHECK (status IN (
        'PROCESSING',
        'SUCCESS',
        'FAILED',
        'PENDING',
        'UNAVAILABLE',
        'INVALID_RESULT'
    ))
);

CREATE UNIQUE INDEX uq_refund_provider_reference
    ON refund_provider_attempts(provider, provider_reference)
    WHERE provider_reference IS NOT NULL;

CREATE INDEX idx_refund_provider_attempt_status
    ON refund_provider_attempts(status);

ALTER TABLE order_items
    ADD COLUMN refund_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REFUNDED',
    ADD COLUMN refunded_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_order_item_refund_status
        CHECK (refund_status IN ('NOT_REFUNDED', 'REFUNDED'));

UPDATE order_items item
SET refund_status = 'REFUNDED',
    refunded_at = COALESCE(escrow.updated_at, NOW())
FROM escrow_ledger escrow
WHERE escrow.order_item_id = item.id
  AND escrow.status = 'REFUNDED';

CREATE INDEX idx_order_items_refund_status
    ON order_items(order_id, refund_status);

-- WalletService performs a lookup before inserting, but the database remains
-- the final concurrency guarantee for the immutable refund ledger.
CREATE UNIQUE INDEX uq_wallet_refund_ledger
    ON wallet_transactions(reference_type, reference_id, transaction_type)
    WHERE reference_id IS NOT NULL
      AND transaction_type = 'REFUND';

ALTER TABLE notifications
    ADD COLUMN dedupe_key VARCHAR(200);

CREATE UNIQUE INDEX uq_notifications_dedupe_key
    ON notifications(dedupe_key)
    WHERE dedupe_key IS NOT NULL;

-- System Admin may act only through the same live DB permissions as Finance.
-- Course Manager deliberately receives neither permission.
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission
  ON permission.code IN ('REFUND_REVIEW', 'FINANCE_EVIDENCE_VIEW')
WHERE role.code = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;
