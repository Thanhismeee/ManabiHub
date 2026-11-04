-- V055: Align refund eligibility and duplicate protection with SRS BR-REF-01..03.
-- Auto eligibility: within 14 calendar days, progress <= 20%, and protected
-- learning materials have not all been downloaded.

UPDATE system_settings
SET setting_value = '14',
    updated_at = NOW()
WHERE setting_key = 'REFUND_WINDOW_DAYS';

UPDATE system_settings
SET setting_value = '20',
    updated_at = NOW()
WHERE setting_key = 'REFUND_PROGRESS_LIMIT_PERCENT';

UPDATE system_settings
SET setting_value = 'br-ref-01-2026-08-03',
    updated_at = NOW()
WHERE setting_key = 'POLICY_VERSION';

UPDATE system_settings
SET setting_value = '2026-08-03T00:00:00Z',
    updated_at = NOW()
WHERE setting_key = 'POLICY_EFFECTIVE_AT';

ALTER TABLE enrollments
    ADD COLUMN protected_materials_fully_downloaded_at TIMESTAMPTZ;

-- BR-REF-03 is order-scoped. Serialize duplicate legacy active requests before
-- replacing the older order-item-scoped database guarantee.
WITH ranked_active_requests AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY order_id
               ORDER BY
                   CASE status
                       WHEN 'APPROVED' THEN 0
                       WHEN 'PROCESSING' THEN 1
                       WHEN 'RECONCILIATION_REQUIRED' THEN 2
                       ELSE 3
                   END,
                   created_at,
                   id
           ) AS active_rank
    FROM refund_requests
    WHERE status IN (
        'PENDING',
        'PROCESSING',
        'RECONCILIATION_REQUIRED',
        'APPROVED'
    )
)
UPDATE refund_requests request
SET status = 'CANCELLED',
    reconciliation_reason_code = 'LEGACY_DUPLICATE_ACTIVE_ORDER_REQUEST',
    updated_at = NOW()
FROM ranked_active_requests ranked
WHERE request.id = ranked.id
  AND ranked.active_rank > 1;

DROP INDEX IF EXISTS uq_refund_request_active_order_item;

CREATE UNIQUE INDEX uq_refund_request_active_order
    ON refund_requests(order_id)
    WHERE status IN (
        'PENDING',
        'PROCESSING',
        'RECONCILIATION_REQUIRED',
        'APPROVED'
    );
