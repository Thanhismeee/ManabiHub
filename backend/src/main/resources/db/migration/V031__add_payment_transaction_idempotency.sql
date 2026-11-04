-- MHB-34 / UC-08: payment webhook idempotency.
-- A provider transaction id must map to at most one payment_transactions row, so a
-- duplicate/replayed VNPay IPN callback cannot create a second SUCCESS record.
-- Partial index (WHERE ... IS NOT NULL) so PENDING rows, which have no provider id yet,
-- are not constrained.

CREATE UNIQUE INDEX uq_payment_transactions_provider_txn
    ON payment_transactions (provider, provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;
