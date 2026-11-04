export interface NotificationResponse {
  id: string;
  title: string;
  message: string;
  notificationType: string;
  actionUrl?: string;
  read: boolean;
  createdAt: string;
  readAt: string | null;
}

export interface NotificationQueryParams {
  type?: string;
  isRead?: boolean;
  page?: number;
  size?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

// Notification type config for UI display
export interface NotificationTypeConfig {
  label: string;
  color: string;
  bgColor: string;
  icon: string;
  important?: boolean;
}

export const NOTIFICATION_TYPES: Record<string, NotificationTypeConfig> = {
  COHORT_ASSIGNMENT: { label: 'Bài tập theo lớp', color: '#1565C0', bgColor: '#E3F2FD', icon: '📚' },
  COHORT_ENROLLMENT: { label: 'Được thêm vào lớp học', color: '#047857', bgColor: '#D1FAE5', icon: '📚' },
  COHORT_SUBMITTED: { label: 'Bài tập mới cần chữa', color: '#7C3AED', bgColor: '#EDE9FE', icon: '✍️' },
  COHORT_FEEDBACK: { label: 'Phản hồi bài tập', color: '#047857', bgColor: '#D1FAE5', icon: '💬' },
  COHORT_REMINDER: { label: 'Nhắc hoàn thành bài tập', color: '#B45309', bgColor: '#FEF3C7', icon: '⏰', important: true },
  PAYMENT: {
    label: 'Thanh toán', color: '#E65100', bgColor: '#FFF3E0', icon: '💳', important: true,
  },
  COURSE: {
    label: 'Khóa học', color: '#2E7D32', bgColor: '#E8F5E9', icon: '📚',
  },
  AI_FEEDBACK: {
    label: 'Phản hồi từ trợ lý AI', color: '#1565C0', bgColor: '#E3F2FD', icon: '✨',
  },
  PURCHASE_SUCCESS: {
    label: 'Mua khóa học',
    color: '#E65100',
    bgColor: '#FFF3E0',
    icon: '💳',
    important: true,
  },
  PAYMENT_FAILED: {
    label: 'Thanh toán thất bại',
    color: '#B91C1C',
    bgColor: '#FEE2E2',
    icon: '⚠️',
    important: true,
  },
  WALLET_TOPUP_SUCCESS: {
    label: 'Nạp ví', color: '#047857', bgColor: '#D1FAE5', icon: '💰', important: true,
  },
  TEACHER_SALE: {
    label: 'Doanh thu mới', color: '#047857', bgColor: '#D1FAE5', icon: '📈', important: true,
  },
  STUDENT_COURSE_COMMENT: {
    label: 'Bình luận mới', color: '#1D4ED8', bgColor: '#DBEAFE', icon: '💬', important: true,
  },
  STUDENT_COURSE_RATING: {
    label: 'Đánh giá mới', color: '#B45309', bgColor: '#FEF3C7', icon: '⭐', important: true,
  },
  TEACHER_REVIEW_REPLY: {
    label: 'Giảng viên phản hồi', color: '#047857', bgColor: '#D1FAE5', icon: '↩️', important: true,
  },
  TEACHER_WRITING_FEEDBACK: {
    label: 'Phản hồi bài viết',
    color: '#1565C0',
    bgColor: '#E3F2FD',
    icon: '✨',
  },
  WRITING_SUBMITTED: {
    label: 'Bài viết mới', color: '#7C3AED', bgColor: '#EDE9FE', icon: '✍️', important: true,
  },
  REFUND: {
    label: 'Hoàn tiền',
    color: '#6A1B9A',
    bgColor: '#F3E5F5',
    icon: '↩',
    important: true,
  },
  COURSE_REVIEW: {
    label: 'Khóa học chờ duyệt', color: '#2E7D32', bgColor: '#E8F5E9', icon: '📚', important: true,
  },
  COURSE_APPROVAL: {
    label: 'Kết quả duyệt khóa học', color: '#2E7D32', bgColor: '#E8F5E9', icon: '✅', important: true,
  },
  COURSE_COMPLETED: {
    label: 'Hoàn thành khóa học',
    color: '#2E7D32',
    bgColor: '#E8F5E9',
    icon: '🎓',
    important: true,
  },
  KYC_CERTIFICATE_PENDING: {
    label: 'KYC đang xử lý', color: '#7C3AED', bgColor: '#EDE9FE', icon: '🪪',
  },
  KYC_CERTIFICATE_REVIEW: {
    label: 'KYC chờ duyệt', color: '#7C3AED', bgColor: '#EDE9FE', icon: '🔎', important: true,
  },
  KYC_RESULT: {
    label: 'Kết quả KYC', color: '#7C3AED', bgColor: '#EDE9FE', icon: '🛡️', important: true,
  },
  WITHDRAWAL_REQUESTED: {
    label: 'Yêu cầu rút tiền', color: '#9A3412', bgColor: '#FFEDD5', icon: '🏦', important: true,
  },
  PAYOUT_CANCELLED: {
    label: 'Đã hủy rút tiền', color: '#6B7280', bgColor: '#F3F4F6', icon: '↩️',
  },
  PAYOUT_SUCCESS: {
    label: 'Rút tiền thành công', color: '#047857', bgColor: '#D1FAE5', icon: '✅', important: true,
  },
  PAYOUT_PENDING_RETRY: {
    label: 'Rút tiền đang thử lại', color: '#B45309', bgColor: '#FEF3C7', icon: '⏳', important: true,
  },
  PAYOUT_FAILED: {
    label: 'Rút tiền thất bại', color: '#B91C1C', bgColor: '#FEE2E2', icon: '⚠️', important: true,
  },
  PAYOUT_REJECTED: {
    label: 'Yêu cầu rút tiền bị từ chối', color: '#B91C1C', bgColor: '#FEE2E2', icon: '⛔', important: true,
  },
  PAYOUT_ALERT: {
    label: 'Cảnh báo quyết toán', color: '#B91C1C', bgColor: '#FEE2E2', icon: '🚨', important: true,
  },
  VIOLATION_REPORT: {
    label: 'Báo cáo vi phạm', color: '#B91C1C', bgColor: '#FEE2E2', icon: '🚩', important: true,
  },
  MODERATION_DECISION: {
    label: 'Kết quả kiểm duyệt', color: '#4338CA', bgColor: '#E0E7FF', icon: '⚖️', important: true,
  },
  MODERATION_EVIDENCE_REQUIRED: {
    label: 'Yêu cầu bổ sung bằng chứng', color: '#B45309', bgColor: '#FEF3C7', icon: '📎', important: true,
  },
  ADMIN_ROLE_CHANGED: {
    label: 'Thay đổi quyền quản trị', color: '#4338CA', bgColor: '#E0E7FF', icon: '🔐', important: true,
  },
  SYSTEM_SETTING_CHANGED: {
    label: 'Thay đổi cấu hình', color: '#455A64', bgColor: '#ECEFF1', icon: '⚙️', important: true,
  },
  SYSTEM: {
    label: 'Hệ thống',
    color: '#455A64',
    bgColor: '#ECEFF1',
    icon: '⚙',
  },
};

export const NOTIFICATION_TYPE_KEYS = Object.keys(NOTIFICATION_TYPES);

export type ReadFilter = 'ALL' | 'UNREAD' | 'READ';
