-- Restore in-app notifications for successful student actions that happened
-- before the notification event handlers were introduced. Historical rows are
-- deliberately not emailed, and the canonical dedupe keys make this migration
-- safe to run alongside the live event handlers.

INSERT INTO notifications (
    id,
    recipient_user_id,
    title,
    message,
    notification_type,
    action_url,
    dedupe_key,
    is_read,
    created_at
)
SELECT
    gen_random_uuid(),
    student.user_id,
    'Mua khóa học thành công',
    'Đơn hàng ' || purchase_order.order_code
        || ' đã được thanh toán thành công. Bạn có thể bắt đầu học ngay bây giờ.',
    'PURCHASE_SUCCESS',
    '/student/courses',
    'payment:' || purchase_order.id || ':' || student.user_id || ':PURCHASE_SUCCESS',
    FALSE,
    COALESCE(purchase_order.updated_at, purchase_order.created_at, NOW())
FROM orders purchase_order
JOIN student_profiles student ON student.id = purchase_order.student_id
WHERE purchase_order.order_status = 'PAID'
  AND purchase_order.order_type = 'COURSE'
ON CONFLICT DO NOTHING;

INSERT INTO notifications (
    id,
    recipient_user_id,
    title,
    message,
    notification_type,
    action_url,
    dedupe_key,
    is_read,
    created_at
)
SELECT
    gen_random_uuid(),
    student.user_id,
    'Nạp ví thành công',
    'Đơn nạp ví ' || top_up_order.order_code || ' ('
        || trim(to_char(top_up_order.total_amount, 'FM999999999999990.00'))
        || 'đ) đã được xử lý thành công. Số dư ví của bạn đã được cập nhật.',
    'WALLET_TOPUP_SUCCESS',
    '/student/wallet',
    'payment:' || top_up_order.id || ':' || student.user_id || ':WALLET_TOPUP_SUCCESS',
    FALSE,
    COALESCE(top_up_order.updated_at, top_up_order.created_at, NOW())
FROM orders top_up_order
JOIN student_profiles student ON student.id = top_up_order.student_id
WHERE top_up_order.order_status = 'PAID'
  AND top_up_order.order_type = 'WALLET_TOPUP'
ON CONFLICT DO NOTHING;

INSERT INTO notifications (
    id,
    recipient_user_id,
    title,
    message,
    notification_type,
    action_url,
    dedupe_key,
    is_read,
    created_at
)
SELECT
    gen_random_uuid(),
    student.user_id,
    'Chúc mừng bạn đã hoàn thành khóa học',
    'Bạn đã hoàn thành khóa học "' || certificate.course_title
        || '" và chứng chỉ đã sẵn sàng.',
    'COURSE_COMPLETED',
    '/student/courses/' || enrollment.course_id || '/learn',
    'course-completed:' || enrollment.id,
    FALSE,
    COALESCE(certificate.issued_at, enrollment.completed_at, NOW())
FROM learning_certificates certificate
JOIN enrollments enrollment ON enrollment.id = certificate.enrollment_id
JOIN student_profiles student ON student.id = enrollment.student_id
ON CONFLICT DO NOTHING;
