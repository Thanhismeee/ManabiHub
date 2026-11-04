import type { RefundDecisionReasonCode, RefundProviderStatus, RefundStatus } from './types';

export const REFUND_STATUS_LABELS: Record<RefundStatus, string> = {
  PENDING: 'Chờ quyết định nội bộ',
  PROCESSING: 'Đã duyệt, provider đang xử lý',
  APPROVED: 'Provider / ví đã hoàn tiền',
  REJECTED: 'Đã từ chối nội bộ',
  RECONCILIATION_REQUIRED: 'Cần đối soát',
  CANCELLED: 'Đã hủy',
};

export const REFUND_PROVIDER_STATUS_LABELS: Record<RefundProviderStatus, string> = {
  NOT_REQUESTED: 'Chưa gửi provider',
  PROCESSING: 'Provider đang xử lý',
  SUCCESS: 'Provider xác nhận thành công',
  FAILED: 'Provider báo thất bại',
  PENDING: 'Provider đang chờ',
  UNAVAILABLE: 'Provider không khả dụng',
  INVALID_RESULT: 'Phản hồi provider không hợp lệ',
};

export const REFUND_DECISION_REASON_LABELS: Record<RefundDecisionReasonCode, string> = {
  STANDARD_ELIGIBLE: 'Đủ điều kiện theo chính sách',
  DUPLICATE_CHARGE: 'Giao dịch bị tính phí trùng',
  CONFIRMED_PAYMENT_ERROR: 'Lỗi thanh toán đã xác minh',
  PLATFORM_ACCESS_FAILURE: 'Lỗi truy cập do nền tảng',
  OUTSIDE_REFUND_WINDOW: 'Ngoài thời hạn hoàn tiền',
  PROGRESS_LIMIT_REACHED: 'Đã đạt/vượt ngưỡng tiến độ',
  PROTECTED_CONTENT_CONSUMED: 'Đã dùng nội dung được bảo vệ',
  PAYMENT_NOT_CONFIRMED: 'Thanh toán chưa được xác nhận',
  DUPLICATE_REQUEST: 'Yêu cầu bị trùng',
  OTHER: 'Lý do khác',
};

export function refundProviderStatusLabel(status?: string | null) {
  if (!status) return 'Chưa ghi nhận';
  return REFUND_PROVIDER_STATUS_LABELS[status as RefundProviderStatus]
    ?? 'Trạng thái provider chưa được ánh xạ';
}

export function refundPaymentStatusLabel(status?: string | null) {
  const labels: Record<string, string> = {
    SUCCESS: 'Thanh toán thành công',
    REFUNDED: 'Thanh toán đã hoàn',
    PENDING: 'Thanh toán đang chờ',
    FAILED: 'Thanh toán thất bại',
    CANCELLED: 'Thanh toán đã hủy',
  };
  return status ? labels[status] ?? 'Trạng thái thanh toán chưa được ánh xạ' : 'Chưa ghi nhận';
}

export function refundEligibilityLabel(value?: unknown) {
  if (value === true || value === 'STANDARD_ELIGIBLE') return 'Có — đủ điều kiện chuẩn';
  if (value === false || value === 'STANDARD_INELIGIBLE') return 'Không — không đủ điều kiện chuẩn';
  if (value === 'MANUAL_REVIEW_REQUIRED') return 'Cần Finance xem xét thủ công';
  return 'Chưa ghi nhận kết quả';
}

export function eligibilityReasonLabel(code: string) {
  const labels: Record<string, string> = {
    PAYMENT_CONFIRMED: 'Thanh toán đã được xác nhận',
    WITHIN_REFUND_WINDOW: 'Yêu cầu còn trong thời hạn hoàn tiền',
    PROGRESS_NOT_ABOVE_THRESHOLD: 'Tiến độ chưa vượt ngưỡng chính sách',
    PROTECTED_MATERIALS_NOT_FULLY_DOWNLOADED: 'Chưa tải toàn bộ tài liệu được bảo vệ',
    NO_ACTIVE_REFUND: 'Không có yêu cầu hoàn tiền khác đang hoạt động',
    ENROLLMENT_PRESENT: 'Có bản ghi tham gia khóa học',
    ENROLLMENT_MISSING: 'Không tìm thấy bản ghi tham gia khóa học',
    OUTSIDE_REFUND_WINDOW: 'Đã ngoài thời hạn hoàn tiền',
    PROGRESS_LIMIT_EXCEEDED: 'Tiến độ đã vượt ngưỡng chính sách',
    PROTECTED_MATERIALS_FULLY_DOWNLOADED: 'Đã tải toàn bộ tài liệu được bảo vệ',
    MANUAL_REVIEW_STANDARD: 'Yêu cầu chuẩn cần xem xét thủ công',
    MANUAL_REVIEW_DUPLICATE_CHARGE: 'Ngoại lệ tính phí trùng cần xem xét',
    MANUAL_REVIEW_PAYMENT_ERROR: 'Ngoại lệ lỗi thanh toán cần xem xét',
    MANUAL_REVIEW_PLATFORM_ACCESS_FAILURE: 'Ngoại lệ lỗi truy cập nền tảng cần xem xét',
  };
  return labels[code] ?? 'Lý do kỹ thuật chưa được ánh xạ';
}
