package com.manabihub.notification;

/**
 * Canonical notification type identifiers shared by all backend workflows.
 * Keep the matching frontend catalogue in features/notifications/types in sync.
 */
public final class NotificationTypes {

    public static final String PURCHASE_SUCCESS = "PURCHASE_SUCCESS";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String WALLET_TOPUP_SUCCESS = "WALLET_TOPUP_SUCCESS";
    public static final String TEACHER_SALE = "TEACHER_SALE";
    public static final String STUDENT_COURSE_COMMENT = "STUDENT_COURSE_COMMENT";
    public static final String STUDENT_COURSE_RATING = "STUDENT_COURSE_RATING";
    public static final String TEACHER_REVIEW_REPLY = "TEACHER_REVIEW_REPLY";
    public static final String COURSE_REVIEW = "COURSE_REVIEW";
    public static final String COURSE_APPROVAL = "COURSE_APPROVAL";
    public static final String KYC_CERTIFICATE_PENDING = "KYC_CERTIFICATE_PENDING";
    public static final String KYC_CERTIFICATE_REVIEW = "KYC_CERTIFICATE_REVIEW";
    public static final String KYC_RESULT = "KYC_RESULT";
    public static final String TEACHER_WRITING_FEEDBACK = "TEACHER_WRITING_FEEDBACK";
    public static final String WRITING_SUBMITTED = "WRITING_SUBMITTED";
    public static final String REFUND = "REFUND";
    public static final String VIOLATION_REPORT = "VIOLATION_REPORT";
    public static final String MODERATION_DECISION = "MODERATION_DECISION";
    public static final String MODERATION_EVIDENCE_REQUIRED = "MODERATION_EVIDENCE_REQUIRED";
    public static final String WITHDRAWAL_REQUESTED = "WITHDRAWAL_REQUESTED";
    public static final String PAYOUT_CANCELLED = "PAYOUT_CANCELLED";
    public static final String PAYOUT_SUCCESS = "PAYOUT_SUCCESS";
    public static final String PAYOUT_PENDING_RETRY = "PAYOUT_PENDING_RETRY";
    public static final String PAYOUT_FAILED = "PAYOUT_FAILED";
    public static final String PAYOUT_REJECTED = "PAYOUT_REJECTED";
    public static final String PAYOUT_ALERT = "PAYOUT_ALERT";
    public static final String COURSE_COMPLETED = "COURSE_COMPLETED";
    public static final String ADMIN_ROLE_CHANGED = "ADMIN_ROLE_CHANGED";
    public static final String SYSTEM_SETTING_CHANGED = "SYSTEM_SETTING_CHANGED";
    public static final String COHORT_ASSIGNMENT = "COHORT_ASSIGNMENT";
    public static final String COHORT_ENROLLMENT = "COHORT_ENROLLMENT";
    public static final String COHORT_SUBMITTED = "COHORT_SUBMITTED";
    public static final String COHORT_FEEDBACK = "COHORT_FEEDBACK";
    public static final String COHORT_REMINDER = "COHORT_REMINDER";
    public static final String OPERATIONAL_DECISION_WARNING = "OPERATIONAL_DECISION_WARNING";

    /**
     * User-facing label used in notification emails. Technical identifiers are
     * deliberately kept out of customer communication.
     */
    public static String vietnameseLabel(String type) {
        if (type == null || type.isBlank()) {
            return "Thông báo hệ thống";
        }
        return switch (type) {
            case COHORT_ASSIGNMENT -> "Bài tập theo lớp";
            case COHORT_ENROLLMENT -> "Được thêm vào lớp học";
            case COHORT_SUBMITTED -> "Bài tập mới cần chữa";
            case COHORT_FEEDBACK -> "Phản hồi bài tập";
            case COHORT_REMINDER -> "Nhắc hoàn thành bài tập";
            case PURCHASE_SUCCESS, "PAYMENT" -> "Thanh toán khóa học";
            case PAYMENT_FAILED -> "Thanh toán chưa thành công";
            case WALLET_TOPUP_SUCCESS -> "Nạp ví";
            case TEACHER_SALE -> "Doanh thu khóa học";
            case STUDENT_COURSE_COMMENT -> "Bình luận mới từ học viên";
            case STUDENT_COURSE_RATING -> "Đánh giá mới từ học viên";
            case TEACHER_REVIEW_REPLY -> "Phản hồi từ giảng viên";
            case COURSE_REVIEW -> "Khóa học chờ duyệt";
            case COURSE_APPROVAL -> "Kết quả duyệt khóa học";
            case KYC_CERTIFICATE_PENDING -> "Chứng chỉ đang được xác minh";
            case KYC_CERTIFICATE_REVIEW -> "Chứng chỉ chờ duyệt";
            case KYC_RESULT -> "Kết quả xác minh giảng viên";
            case TEACHER_WRITING_FEEDBACK -> "Phản hồi bài viết";
            case WRITING_SUBMITTED -> "Bài viết mới cần phản hồi";
            case REFUND -> "Hoàn tiền";
            case VIOLATION_REPORT -> "Báo cáo vi phạm";
            case MODERATION_DECISION -> "Kết quả kiểm duyệt";
            case MODERATION_EVIDENCE_REQUIRED -> "Yêu cầu bổ sung bằng chứng";
            case WITHDRAWAL_REQUESTED -> "Yêu cầu rút tiền";
            case PAYOUT_CANCELLED -> "Đã hủy yêu cầu rút tiền";
            case PAYOUT_SUCCESS -> "Rút tiền thành công";
            case PAYOUT_PENDING_RETRY -> "Yêu cầu rút tiền đang được xử lý lại";
            case PAYOUT_FAILED -> "Rút tiền chưa thành công";
            case PAYOUT_REJECTED -> "Yêu cầu rút tiền bị từ chối";
            case PAYOUT_ALERT -> "Cảnh báo quyết toán";
            case COURSE_COMPLETED -> "Hoàn thành khóa học";
            case ADMIN_ROLE_CHANGED -> "Thay đổi quyền quản trị";
            case SYSTEM_SETTING_CHANGED, "SYSTEM" -> "Cấu hình hệ thống";
            case "COURSE" -> "Khóa học";
            case "AI_FEEDBACK" -> "Phản hồi từ trợ lý AI";
            default -> "Thông báo ManabiHub";
        };
    }

    private NotificationTypes() {
    }
}
