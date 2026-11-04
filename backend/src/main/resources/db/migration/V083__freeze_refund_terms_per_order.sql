-- MHB-021: commission was already frozen per order item at payment time, but the refund
-- terms were not recorded anywhere, so changing REFUND_WINDOW_DAYS or
-- REFUND_PROGRESS_LIMIT_PERCENT retroactively changed the refund rights of orders already
-- sold. The refund terms now join commission in the immutable snapshot.
ALTER TABLE order_item_snapshots
    ADD COLUMN refund_window_days INT,
    ADD COLUMN refund_progress_limit_percent INT;

-- Rows written before this migration stay NULL on purpose. The terms in force at their
-- purchase were never recorded and cannot be reconstructed; inventing a value would silently
-- rewrite the refund rights of real orders. For those rows only, the service keeps reading
-- the current policy, which is exactly today's behaviour.
ALTER TABLE order_item_snapshots
    ADD CONSTRAINT chk_order_item_snapshot_refund_window
        CHECK (refund_window_days IS NULL
            OR (refund_window_days >= 0 AND refund_window_days <= 365)),
    ADD CONSTRAINT chk_order_item_snapshot_refund_progress_limit
        CHECK (refund_progress_limit_percent IS NULL
            OR (refund_progress_limit_percent >= 0 AND refund_progress_limit_percent <= 100));