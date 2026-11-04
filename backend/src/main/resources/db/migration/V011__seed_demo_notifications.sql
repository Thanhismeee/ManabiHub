-- V011__seed_demo_notifications.sql
-- Mock notifications covering all types defined in frontend (PAYMENT, AI_FEEDBACK, REFUND, COURSE, SYSTEM)
-- For Student (d0000000-0000-0000-0000-000000000001)
-- For Teacher (d0000000-0000-0000-0000-000000000002)

-- 1. Student Notifications
INSERT INTO notifications (recipient_user_id, title, message, notification_type, is_read, created_at)
VALUES
    ('d0000000-0000-0000-0000-000000000001', 'Thanh toán thành công', 'Bạn đã thanh toán thành công khoá học "Tiếng Nhật N4 Cấp tốc" qua VNPay.', 'PAYMENT', false, NOW() - INTERVAL '1 hour'),
    ('d0000000-0000-0000-0000-000000000001', 'Phản hồi từ AI', 'AI đã nhận xét bài phát âm của bạn. Có 2 lỗi cần khắc phục, hãy vào xem ngay.', 'AI_FEEDBACK', false, NOW() - INTERVAL '3 hours'),
    ('d0000000-0000-0000-0000-000000000001', 'Cập nhật khoá học', 'Giáo viên vừa thêm bài giảng mới vào khoá "Giao tiếp cơ bản".', 'COURSE', true, NOW() - INTERVAL '1 day'),
    ('d0000000-0000-0000-0000-000000000001', 'Thông báo hệ thống', 'Hệ thống sẽ bảo trì từ 00:00 đến 02:00 ngày mai.', 'SYSTEM', true, NOW() - INTERVAL '2 days'),
    ('d0000000-0000-0000-0000-000000000001', 'Yêu cầu hoàn tiền được chấp nhận', 'Yêu cầu hoàn tiền khoá học JLPT N5 của bạn đã được chấp nhận và đang xử lý.', 'REFUND', false, NOW() - INTERVAL '5 days');

-- 2. Teacher Notifications
INSERT INTO notifications (recipient_user_id, title, message, notification_type, is_read, created_at)
VALUES
    ('d0000000-0000-0000-0000-000000000002', 'Khoá học đã được xuất bản', 'Khoá học "Tiếng Nhật N3" của bạn đã được Admin phê duyệt.', 'COURSE', false, NOW() - INTERVAL '30 minutes'),
    ('d0000000-0000-0000-0000-000000000002', 'Doanh thu tháng 6', 'Bạn đã nhận được thanh toán doanh thu khoá học tháng 6.', 'PAYMENT', true, NOW() - INTERVAL '1 day'),
    ('d0000000-0000-0000-0000-000000000002', 'Hệ thống đánh giá', 'Tính năng đánh giá bài tập tự động bằng AI đã được bật cho khoá học của bạn.', 'SYSTEM', false, NOW() - INTERVAL '2 days');

-- 3. Admin Notifications (if needed by another view, using ID c0000000-0000-0000-0000-000000000001)
INSERT INTO notifications (recipient_admin_id, title, message, notification_type, is_read, created_at)
VALUES
    ('c0000000-0000-0000-0000-000000000001', 'Yêu cầu hoàn tiền mới', 'Học viên A yêu cầu hoàn tiền khoá học N2. Vui lòng kiểm tra.', 'REFUND', false, NOW() - INTERVAL '2 hours'),
    ('c0000000-0000-0000-0000-000000000001', 'Yêu cầu xuất bản khoá học', 'Giáo viên B vừa gửi yêu cầu xuất bản khoá học mới.', 'COURSE', false, NOW() - INTERVAL '4 hours');
