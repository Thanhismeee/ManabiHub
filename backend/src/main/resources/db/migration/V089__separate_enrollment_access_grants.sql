-- The old projection could overwrite a paid deadline with a class deadline.
-- Do not invent the paid deadline from today's course settings during migration.
DO $$ BEGIN
    IF EXISTS (
        SELECT 1 FROM enrollments e JOIN orders o ON o.student_id=e.student_id
        JOIN order_items oi ON oi.order_id=o.id AND oi.course_id=e.course_id
        WHERE e.access_source='SOFL_COHORT' AND o.order_status='PAID' AND oi.refund_status='NOT_REFUNDED'
    ) THEN RAISE EXCEPTION 'V089 blocked: paid enrollments carry class provenance; reconcile their original paid state/deadline before retrying'; END IF;
END $$;

CREATE TABLE enrollment_access_grants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
    access_source VARCHAR(30) NOT NULL CHECK (access_source IN ('PURCHASE','SOFL_COHORT','ADMIN_GRANT')),
    source_reference_id UUID NOT NULL,
    cohort_id UUID REFERENCES learning_cohorts(id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('ACTIVE','REFUND_PENDING','REFUNDED','REVOKED','EXPIRED')),
    granted_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    UNIQUE (access_source, source_reference_id)
);
CREATE INDEX idx_access_grants_enrollment ON enrollment_access_grants(enrollment_id, status);

-- Existing purchases retain their deadline and state. Match the latest settled order item.
INSERT INTO enrollment_access_grants(enrollment_id,access_source,source_reference_id,status,granted_at,expires_at)
SELECT e.id,e.access_source,COALESCE(item.id,e.id),
       CASE WHEN e.enrollment_status='COMPLETED' THEN 'ACTIVE' ELSE e.enrollment_status END,
       e.enrolled_at,e.expires_at
FROM enrollments e
LEFT JOIN LATERAL (
    SELECT oi.id FROM order_items oi JOIN orders o ON o.id=oi.order_id
    WHERE o.student_id=e.student_id AND oi.course_id=e.course_id AND o.order_status IN ('PAID','REFUNDED')
    ORDER BY o.created_at DESC,oi.id LIMIT 1
) item ON e.access_source='PURCHASE'
WHERE e.access_source IN ('PURCHASE','ADMIN_GRANT');

-- Class rights are independent even when the existing projection still represents a purchase.
INSERT INTO enrollment_access_grants(enrollment_id,access_source,source_reference_id,cohort_id,status,granted_at,expires_at)
SELECT e.id,'SOFL_COHORT',m.id,c.id,
       CASE WHEN m.status='ACTIVE' AND c.status IN ('ACTIVE','PLANNED') THEN 'ACTIVE' ELSE 'REVOKED' END,
       m.joined_at,(c.end_date+1)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM cohort_members m JOIN learning_cohorts c ON c.id=m.cohort_id
JOIN enrollments e ON e.student_id=m.student_id AND e.course_id=c.course_id;

-- Materialize effective access so existing read APIs immediately respect both sources.
UPDATE enrollments e SET
    enrollment_status=CASE WHEN a.revoked THEN 'REVOKED' WHEN a.pending THEN 'REFUND_PENDING'
        WHEN a.valid_count>0 THEN CASE WHEN e.completed_at IS NULL THEN 'ACTIVE' ELSE 'COMPLETED' END
        WHEN a.refunded THEN 'REFUNDED' ELSE 'EXPIRED' END,
    expires_at=CASE WHEN a.valid_count=0 THEN NOW() WHEN a.unlimited THEN NULL ELSE a.deadline END,
    source_cohort_id=CASE WHEN a.valid_count=0 THEN NULL ELSE e.source_cohort_id END
FROM (
    SELECT enrollment_id,
       bool_or(access_source='PURCHASE' AND status='REVOKED') AS revoked,
       bool_or(access_source='PURCHASE' AND status='REFUND_PENDING') AS pending,
       bool_or(access_source='PURCHASE' AND status='REFUNDED') AS refunded,
       count(*) FILTER(WHERE status='ACTIVE' AND (expires_at IS NULL OR expires_at>NOW())) AS valid_count,
       bool_or(status='ACTIVE' AND expires_at IS NULL) AS unlimited,
       max(expires_at) FILTER(WHERE status='ACTIVE' AND expires_at>NOW()) AS deadline
    FROM enrollment_access_grants GROUP BY enrollment_id
) a WHERE a.enrollment_id=e.id;

UPDATE enrollments e SET access_source=g.access_source,source_cohort_id=g.cohort_id
FROM (
    SELECT DISTINCT ON(enrollment_id) enrollment_id,access_source,cohort_id
    FROM enrollment_access_grants WHERE status='ACTIVE' AND (expires_at IS NULL OR expires_at>NOW())
    ORDER BY enrollment_id,(access_source='PURCHASE') DESC,expires_at DESC NULLS FIRST,id
) g WHERE g.enrollment_id=e.id;
