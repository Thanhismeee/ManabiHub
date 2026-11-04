-- V044: immutable commercial snapshots and append-only commission events.
--
-- Historical paid orders were created before commission allocation existed.
-- Preserve that actual accounting state with a 0% legacy snapshot instead of
-- retroactively charging teachers a commission that was never withheld.

CREATE TABLE order_item_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_item_id UUID NOT NULL UNIQUE REFERENCES order_items(id),
    currency VARCHAR(10) NOT NULL,
    gross_amount NUMERIC(12, 2) NOT NULL,
    commission_rate NUMERIC(5, 4) NOT NULL,
    commission_amount NUMERIC(12, 2) NOT NULL,
    teacher_net_amount NUMERIC(12, 2) NOT NULL,
    gateway_fee_amount NUMERIC(12, 2),
    commercial_policy_version VARCHAR(100) NOT NULL,
    escrow_days INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_order_item_snapshot_currency
        CHECK (currency ~ '^[A-Z]{3,10}$'),
    CONSTRAINT chk_order_item_snapshot_gross
        CHECK (gross_amount >= 0),
    CONSTRAINT chk_order_item_snapshot_commission_rate
        CHECK (commission_rate >= 0 AND commission_rate <= 1),
    CONSTRAINT chk_order_item_snapshot_commission
        CHECK (commission_amount >= 0),
    CONSTRAINT chk_order_item_snapshot_teacher_net
        CHECK (teacher_net_amount >= 0),
    CONSTRAINT chk_order_item_snapshot_gateway_fee
        CHECK (gateway_fee_amount IS NULL OR gateway_fee_amount >= 0),
    CONSTRAINT chk_order_item_snapshot_split
        CHECK (gross_amount = commission_amount + teacher_net_amount),
    CONSTRAINT chk_order_item_snapshot_escrow_days
        CHECK (escrow_days BETWEEN 1 AND 365)
);

ALTER TABLE escrow_ledger
    ADD COLUMN order_item_id UUID REFERENCES order_items(id);

-- The legacy schema identifies an escrow by order and course. Backfill only
-- unambiguous rows, then fail the migration if financial history cannot be
-- reconciled exactly. Money migrations must never guess an allocation.
UPDATE escrow_ledger escrow
SET order_item_id = item.id
FROM order_items item
WHERE escrow.order_item_id IS NULL
  AND item.order_id = escrow.order_id
  AND item.course_id = escrow.course_id
  AND (
      SELECT COUNT(*)
      FROM order_items candidate
      WHERE candidate.order_id = escrow.order_id
        AND candidate.course_id = escrow.course_id
  ) = 1;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM escrow_ledger
        WHERE order_item_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'V044 requires every legacy escrow to map to exactly one order item';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM order_items item
        JOIN orders purchase ON purchase.id = item.order_id
        LEFT JOIN escrow_ledger escrow ON escrow.order_item_id = item.id
        WHERE purchase.order_status = 'PAID'
        GROUP BY item.id
        HAVING COUNT(escrow.id) <> 1
    ) THEN
        RAISE EXCEPTION
            'V044 requires exactly one escrow allocation per legacy paid order item';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM escrow_ledger escrow
        JOIN order_items item ON item.id = escrow.order_item_id
        JOIN orders purchase ON purchase.id = item.order_id
        WHERE purchase.order_status = 'PAID'
          AND escrow.amount <> item.price
    ) THEN
        RAISE EXCEPTION
            'V044 legacy escrow amount does not match its paid order item';
    END IF;
END;
$$;

ALTER TABLE escrow_ledger
    ALTER COLUMN order_item_id SET NOT NULL;

CREATE UNIQUE INDEX uq_escrow_ledger_order_item
    ON escrow_ledger(order_item_id);

CREATE INDEX idx_escrow_ledger_order_item
    ON escrow_ledger(order_item_id);

-- Before V044, an escrow hold increased only frozen_balance even though
-- wallets.balance is the total balance. Bring existing HELD/FROZEN earnings
-- into the total exactly once so available = balance - frozen_balance remains
-- non-negative and withdrawal reservations keep their existing semantics.
WITH held_escrow_totals AS (
    SELECT teacher_id, SUM(amount) AS held_amount
    FROM escrow_ledger
    WHERE status IN ('HELD', 'FROZEN')
    GROUP BY teacher_id
)
UPDATE wallets wallet
SET balance = wallet.balance + held.held_amount
FROM held_escrow_totals held
WHERE wallet.owner_type = 'TEACHER'
  AND wallet.teacher_id = held.teacher_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM wallets
        WHERE balance < 0
           OR frozen_balance < 0
           OR frozen_balance > balance
    ) THEN
        RAISE EXCEPTION
            'Wallet balance invariant violated during V044 migration';
    END IF;
END;
$$;

ALTER TABLE wallets
    ADD CONSTRAINT chk_wallets_balance_invariant
    CHECK (
        balance >= 0
        AND frozen_balance >= 0
        AND frozen_balance <= balance
    );

INSERT INTO order_item_snapshots (
    id,
    order_item_id,
    currency,
    gross_amount,
    commission_rate,
    commission_amount,
    teacher_net_amount,
    gateway_fee_amount,
    commercial_policy_version,
    escrow_days,
    created_at
)
SELECT
    gen_random_uuid(),
    item.id,
    purchase.currency,
    item.price,
    0,
    0,
    item.price,
    NULL,
    'legacy-pre-v044',
    LEAST(
        365,
        GREATEST(
            1,
            COALESCE(
                CEIL(EXTRACT(EPOCH FROM (escrow.release_at - escrow.created_at)) / 86400.0)::INT,
                14
            )
        )
    ),
    COALESCE(item.created_at, purchase.created_at, NOW())
FROM order_items item
JOIN orders purchase ON purchase.id = item.order_id
LEFT JOIN LATERAL (
    SELECT candidate.release_at, candidate.created_at
    FROM escrow_ledger candidate
    WHERE candidate.order_item_id = item.id
    ORDER BY candidate.created_at ASC, candidate.id ASC
    LIMIT 1
) escrow ON TRUE
WHERE purchase.order_status = 'PAID'
ON CONFLICT (order_item_id) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM escrow_ledger escrow
        JOIN order_item_snapshots snapshot
          ON snapshot.order_item_id = escrow.order_item_id
        WHERE escrow.amount <> snapshot.teacher_net_amount
    ) THEN
        RAISE EXCEPTION
            'V044 escrow amount does not match the immutable teacher net snapshot';
    END IF;
END;
$$;

CREATE TABLE platform_commission_ledgers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    order_item_id UUID NOT NULL REFERENCES order_items(id),
    amount NUMERIC(12, 2) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_platform_commission_amount
        CHECK (amount >= 0),
    CONSTRAINT chk_platform_commission_event
        CHECK (event_type IN (
            'COMMISSION_HELD',
            'COMMISSION_RECOGNIZED',
            'COMMISSION_REVERSED'
        )),
    CONSTRAINT uq_platform_commission_event
        UNIQUE (order_item_id, event_type)
);

CREATE INDEX idx_platform_commission_order
    ON platform_commission_ledgers(order_id);

CREATE INDEX idx_platform_commission_created_at
    ON platform_commission_ledgers(created_at);

-- Establish a complete event trail for legacy paid items. Legacy commission is
-- zero by design, matching the amount that the old application actually held.
INSERT INTO platform_commission_ledgers (
    id, order_id, order_item_id, amount, event_type, created_at
)
SELECT
    gen_random_uuid(),
    item.order_id,
    item.id,
    snapshot.commission_amount,
    'COMMISSION_HELD',
    snapshot.created_at
FROM order_items item
JOIN order_item_snapshots snapshot ON snapshot.order_item_id = item.id
ON CONFLICT (order_item_id, event_type) DO NOTHING;

INSERT INTO platform_commission_ledgers (
    id, order_id, order_item_id, amount, event_type, created_at
)
SELECT
    gen_random_uuid(),
    item.order_id,
    item.id,
    snapshot.commission_amount,
    CASE
        WHEN escrow.status = 'RELEASED' THEN 'COMMISSION_RECOGNIZED'
        ELSE 'COMMISSION_REVERSED'
    END,
    COALESCE(escrow.updated_at, escrow.created_at, NOW())
FROM escrow_ledger escrow
JOIN order_items item ON item.id = escrow.order_item_id
JOIN order_item_snapshots snapshot ON snapshot.order_item_id = item.id
WHERE escrow.status IN ('RELEASED', 'REFUNDED')
ON CONFLICT (order_item_id, event_type) DO NOTHING;

INSERT INTO system_settings (
    id, setting_key, setting_value, value_type, description, is_editable
)
VALUES
    (
        gen_random_uuid(),
        'CURRENCY',
        'VND',
        'STRING',
        'Commercial settlement currency',
        FALSE
    ),
    (
        gen_random_uuid(),
        'WITHDRAWAL_FEE',
        '0',
        'NUMBER',
        'Teacher withdrawal fee in the configured currency',
        TRUE
    ),
    (
        gen_random_uuid(),
        'KYC_TARGET_DAYS_MIN',
        '1',
        'NUMBER',
        'Minimum advertised KYC review target in business days',
        TRUE
    ),
    (
        gen_random_uuid(),
        'KYC_TARGET_DAYS_MAX',
        '2',
        'NUMBER',
        'Maximum advertised KYC review target in business days',
        TRUE
    ),
    (
        gen_random_uuid(),
        'POLICY_VERSION',
        'provisional-2026-07-28',
        'STRING',
        'Version identifier for the active public commercial policy',
        TRUE
    ),
    (
        gen_random_uuid(),
        'POLICY_EFFECTIVE_AT',
        '2026-07-28T00:00:00Z',
        'STRING',
        'ISO-8601 instant when the active commercial policy became effective',
        TRUE
    )
ON CONFLICT (setting_key) DO NOTHING;

CREATE OR REPLACE FUNCTION reject_financial_history_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; % is not allowed', TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER trg_order_item_snapshots_immutable
BEFORE UPDATE OR DELETE ON order_item_snapshots
FOR EACH ROW
EXECUTE FUNCTION reject_financial_history_mutation();

CREATE TRIGGER trg_platform_commission_ledgers_immutable
BEFORE UPDATE OR DELETE ON platform_commission_ledgers
FOR EACH ROW
EXECUTE FUNCTION reject_financial_history_mutation();

CREATE OR REPLACE FUNCTION reject_escrow_allocation_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.order_id IS DISTINCT FROM OLD.order_id
       OR NEW.order_item_id IS DISTINCT FROM OLD.order_item_id
       OR NEW.course_id IS DISTINCT FROM OLD.course_id
       OR NEW.teacher_id IS DISTINCT FROM OLD.teacher_id
       OR NEW.amount IS DISTINCT FROM OLD.amount THEN
        RAISE EXCEPTION
            'escrow_ledger allocation fields are immutable after creation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_escrow_allocation_immutable
BEFORE UPDATE OF order_id, order_item_id, course_id, teacher_id, amount
ON escrow_ledger
FOR EACH ROW
EXECUTE FUNCTION reject_escrow_allocation_mutation();
