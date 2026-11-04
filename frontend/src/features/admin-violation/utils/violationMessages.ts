import axios from 'axios';

interface ApiErrorBody {
  messageCode?: string;
}

const messages: Record<string, string> = {
  'MSG-ADM-002': 'Tài khoản quản trị không có quyền thực hiện thao tác này.',
  ADMIN_PERMISSION_DENIED: 'Tài khoản quản trị không có quyền xử lý báo cáo.',
  MODERATION_SEVERE_PERMISSION_REQUIRED:
    'Bạn không có quyền khóa tài khoản hoặc đóng băng ví.',
  MODERATION_CONTENT_PERMISSION_REQUIRED:
    'Bạn không có quyền áp dụng biện pháp kiểm duyệt nội dung này.',
  MODERATION_ALREADY_RESOLVED:
    'Báo cáo đã được quản trị viên khác xử lý. Dữ liệu sẽ được tải lại.',
  MODERATION_DECISION_NOTE_REQUIRED: 'Vui lòng nhập ghi chú cho quyết định.',
  MODERATION_ACTION_REQUIRED: 'Chọn ít nhất một biện pháp xử lý.',
  MODERATION_INVALID_ACTION:
    'Biện pháp xử lý không phù hợp với đối tượng bị báo cáo.',
  MODERATION_TARGET_NOT_FOUND: 'Đối tượng bị báo cáo không còn tồn tại.',
  MODERATION_REPORT_NOT_FOUND: 'Báo cáo không còn tồn tại.',
  VALIDATION_FAILED: 'Thông tin xử lý chưa hợp lệ.',
  'MSG-COM-002': 'Vui lòng kiểm tra lại thông tin bắt buộc.',
  'MSG-COM-004': 'Không thể áp dụng quyết định kiểm duyệt.',
};

export function getViolationErrorMessage(error: unknown) {
  if (!axios.isAxiosError<ApiErrorBody>(error)) {
    return 'Không thể xử lý báo cáo vi phạm. Vui lòng thử lại.';
  }
  const messageCode = error.response?.data?.messageCode;
  return messageCode && messages[messageCode]
    ? messages[messageCode]
    : 'Không thể xử lý báo cáo vi phạm. Vui lòng thử lại.';
}

export function isViolationConflict(error: unknown) {
  return axios.isAxiosError(error) && error.response?.status === 409;
}
