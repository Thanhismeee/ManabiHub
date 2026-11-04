-- Wallet ledger amounts are magnitudes; wallet_transactions.direction (IN/OUT)
-- carries the sign. Withdrawal entries were the only ones stored negated, which
-- made the transaction history render a double minus ("−-200.000 VND") once the
-- UI started deriving the sign from direction.
--
-- Balances are never computed from this column and reconciliation compares it
-- with ABS(), so rewriting the sign changes presentation only.

UPDATE wallet_transactions
SET amount = ABS(amount)
WHERE amount < 0;

ALTER TABLE wallet_transactions
    ADD CONSTRAINT chk_wallet_transactions_amount_sign
        CHECK (amount >= 0);
