-- Localize notifications created before Vietnamese became the mandatory
-- customer-communication language. Historical emails are not resent.

UPDATE notifications
SET title = 'Khóa học mới đang chờ xét duyệt',
    message = 'Giảng viên đã gửi một khóa học để xét duyệt.'
WHERE notification_type = 'COURSE_REVIEW'
  AND (title = 'Course submitted for review'
       OR message LIKE 'Teacher submitted course%');

UPDATE notifications
SET title = 'Cập nhật kết quả duyệt khóa học',
    message = 'Khóa học của bạn đã được phê duyệt và sẵn sàng để xuất bản.'
WHERE notification_type = 'COURSE_APPROVAL'
  AND (title = 'Course Review Update'
       OR message LIKE 'Your course%');

UPDATE notifications
SET title = 'Giảng viên đã phản hồi bài viết',
    message = 'Bài viết của bạn đã được giảng viên nhận xét. Hãy mở khóa học để xem chi tiết.'
WHERE notification_type = 'TEACHER_WRITING_FEEDBACK'
  AND (title = 'Writing feedback is ready'
       OR message LIKE 'Your teacher has reviewed%');

UPDATE notifications
SET title = 'Nội dung cần được chỉnh sửa'
WHERE notification_type = 'MODERATION_DECISION'
  AND title = 'Content correction required';

UPDATE notifications
SET title = 'Báo cáo vi phạm đã được xác nhận'
WHERE notification_type = 'MODERATION_DECISION'
  AND title = 'Violation report upheld';

UPDATE notifications
SET title = 'Yêu cầu bổ sung bằng chứng',
    message = 'Vui lòng bổ sung bằng chứng cho báo cáo vi phạm này.'
WHERE notification_type = 'MODERATION_EVIDENCE_REQUIRED'
  AND (title = 'Additional evidence required'
       OR message LIKE 'Please provide additional evidence%');

UPDATE notifications
SET message = replace(message, 'được Admin phê duyệt', 'được quản trị viên phê duyệt')
WHERE message LIKE '%được Admin phê duyệt%';
