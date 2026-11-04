-- Student wallet withdrawals.
-- Refund credits are withdrawable; direct wallet top-ups are not.

ALTER TABLE wallets
    ADD COLUMN withdrawable_balance NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN frozen_withdrawable_balance NUMERIC(12, 2) NOT NULL DEFAULT 0;

-- Preserve refund value already present in student wallets, capped by the
-- current total balance. New credits are maintained transactionally by code.
WITH refund_credits AS (
    SELECT wallet_id, COALESCE(SUM(amount), 0) AS amount
    FROM wallet_transactions
    WHERE transaction_type = 'REFUND'
      AND direction = 'IN'
    GROUP BY wallet_id
)
UPDATE wallets wallet
SET withdrawable_balance = LEAST(wallet.balance, credits.amount)
FROM refund_credits credits
WHERE wallet.id = credits.wallet_id
  AND wallet.owner_type = 'STUDENT';

ALTER TABLE wallet_payment_reservations
    ADD COLUMN withdrawable_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

-- Existing order reservations consume non-withdrawable value first. Record
-- the remainder as the withdrawable component so capture/release stays exact.
WITH ordered_reservations AS (
    SELECT reservation.id,
           reservation.amount,
           wallet.balance - wallet.withdrawable_balance AS non_withdrawable_balance,
           SUM(reservation.amount) OVER (
               PARTITION BY reservation.wallet_id
               ORDER BY reservation.reserved_at, reservation.id
           ) AS cumulative_amount
    FROM wallet_payment_reservations reservation
    JOIN wallets wallet ON wallet.id = reservation.wallet_id
    WHERE reservation.status = 'RESERVED'
      AND wallet.owner_type = 'STUDENT'
), reservation_components AS (
    SELECT id,
           GREATEST(0, cumulative_amount - non_withdrawable_balance)
           - GREATEST(0, cumulative_amount - amount - non_withdrawable_balance)
               AS withdrawable_amount
    FROM ordered_reservations
)
UPDATE wallet_payment_reservations reservation
SET withdrawable_amount = component.withdrawable_amount
FROM reservation_components component
WHERE reservation.id = component.id;

WITH frozen_components AS (
    SELECT wallet_id, COALESCE(SUM(withdrawable_amount), 0) AS amount
    FROM wallet_payment_reservations
    WHERE status = 'RESERVED'
    GROUP BY wallet_id
)
UPDATE wallets wallet
SET frozen_withdrawable_balance = LEAST(
        wallet.withdrawable_balance,
        COALESCE(component.amount, 0)
    )
FROM frozen_components component
WHERE wallet.id = component.wallet_id
  AND wallet.owner_type = 'STUDENT';

ALTER TABLE wallets
    ADD CONSTRAINT chk_wallets_withdrawable_balance
        CHECK (
            withdrawable_balance >= 0
            AND withdrawable_balance <= balance
            AND frozen_withdrawable_balance >= 0
            AND frozen_withdrawable_balance <= withdrawable_balance
            AND frozen_withdrawable_balance <= frozen_balance
        );

ALTER TABLE wallet_payment_reservations
    ADD CONSTRAINT chk_wallet_reservation_withdrawable_amount
        CHECK (withdrawable_amount >= 0 AND withdrawable_amount <= amount);

CREATE TABLE student_bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
    bank_code VARCHAR(50) NOT NULL,
    bank_name VARCHAR(255) NOT NULL,
    account_number VARCHAR(512) NOT NULL,
    account_fingerprint VARCHAR(64) NOT NULL,
    account_holder_name VARCHAR(255) NOT NULL,
    branch VARCHAR(255),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_student_bank_accounts_student_id
    ON student_bank_accounts(student_id);

CREATE UNIQUE INDEX uq_student_bank_account_fingerprint
    ON student_bank_accounts(student_id, account_fingerprint);

ALTER TABLE withdrawal_requests
    ADD COLUMN owner_type VARCHAR(30) NOT NULL DEFAULT 'TEACHER',
    ADD COLUMN student_id UUID REFERENCES student_profiles(id),
    ADD COLUMN wallet_id UUID REFERENCES wallets(id);

UPDATE withdrawal_requests request
SET wallet_id = wallet.id
FROM wallets wallet
WHERE wallet.owner_type = 'TEACHER'
  AND wallet.teacher_id = request.teacher_id
  AND request.wallet_id IS NULL;

ALTER TABLE withdrawal_requests
    ALTER COLUMN teacher_id DROP NOT NULL,
    ALTER COLUMN wallet_id SET NOT NULL,
    ADD CONSTRAINT chk_withdrawal_request_owner_type
        CHECK (owner_type IN ('TEACHER', 'STUDENT')),
    ADD CONSTRAINT chk_withdrawal_request_owner
        CHECK (
            (owner_type = 'TEACHER' AND teacher_id IS NOT NULL AND student_id IS NULL)
            OR
            (owner_type = 'STUDENT' AND student_id IS NOT NULL AND teacher_id IS NULL)
        );

CREATE INDEX idx_withdrawal_requests_student_id
    ON withdrawal_requests(student_id);

CREATE INDEX idx_withdrawal_requests_wallet_id
    ON withdrawal_requests(wallet_id);

CREATE UNIQUE INDEX uq_withdrawal_request_pending_student
    ON withdrawal_requests(student_id)
    WHERE status = 'PENDING' AND owner_type = 'STUDENT';

ALTER TABLE payout_settlements
    ADD COLUMN owner_type VARCHAR(30) NOT NULL DEFAULT 'TEACHER',
    ADD COLUMN student_id UUID REFERENCES student_profiles(id);

ALTER TABLE payout_settlements
    ALTER COLUMN teacher_id DROP NOT NULL,
    ADD CONSTRAINT chk_payout_settlement_owner_type
        CHECK (owner_type IN ('TEACHER', 'STUDENT')),
    ADD CONSTRAINT chk_payout_settlement_owner
        CHECK (
            (owner_type = 'TEACHER' AND teacher_id IS NOT NULL AND student_id IS NULL)
            OR
            (owner_type = 'STUDENT' AND student_id IS NOT NULL AND teacher_id IS NULL)
        );

CREATE INDEX idx_payout_settlements_student_id
    ON payout_settlements(student_id);
