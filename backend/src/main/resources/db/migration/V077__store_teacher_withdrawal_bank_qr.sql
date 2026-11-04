-- Store the teacher-selected bank QR privately with the withdrawal request.
-- The columns are nullable for historical requests and student withdrawals.
ALTER TABLE withdrawal_requests
    ADD COLUMN bank_qr_code BYTEA,
    ADD COLUMN bank_qr_content_type VARCHAR(50);
