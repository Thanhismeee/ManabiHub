-- MHB-37 / combined payment: the portion of a course order paid from the student's wallet.
-- The remaining (total_amount - wallet_amount) is charged via the payment gateway (VNPay).
-- Defaults to 0 for existing orders and full-gateway/full-wallet orders.

ALTER TABLE orders ADD COLUMN IF NOT EXISTS wallet_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
