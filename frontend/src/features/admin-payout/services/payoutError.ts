import { isAxiosError } from 'axios';
import type { ApiResponse } from '../../../shared/types/api';

const PAYOUT_ERROR_MESSAGES: Record<string, string> = {
  'MSG-ADM-005': 'Phát hiện sai lệch dữ liệu giao dịch. Vui lòng kiểm tra đối soát.',
  PAYOUT_RECONCILIATION_MISMATCH: 'Phát hiện sai lệch dữ liệu giao dịch. Vui lòng kiểm tra đối soát.',
  PAYOUT_BALANCE_FROZEN: 'Tài khoản giáo viên đang bị khóa nên chưa thể thanh toán.',
  PAYOUT_INSUFFICIENT_RESERVED_BALANCE: 'Số dư đang giữ không đủ cho yêu cầu rút tiền.',
  PAYOUT_SETTLEMENT_PROCESSING: 'Yêu cầu đang được một quản lý tài chính khác xử lý.',
  PAYOUT_PENDING_RETRY: 'Cổng thanh toán chưa phản hồi ổn định. Yêu cầu đã được giữ để thử lại an toàn.',
  PAYOUT_GATEWAY_FAILED: 'Cổng thanh toán từ chối giao dịch. Tiền đang giữ vẫn được bảo toàn.',
  PAYOUT_INVALID_STATUS: 'Yêu cầu không còn ở trạng thái cho phép thực hiện thao tác này.',
  PAYOUT_DUPLICATE_SETTLEMENT: 'Yêu cầu này đã có giao dịch chi trả.',
  PAYOUT_PERMISSION_DENIED: 'Bạn không có quyền Finance Manager để xử lý chi trả.',
  PAYOUT_NOT_FOUND: 'Không tìm thấy yêu cầu rút tiền.',
  PAYOUT_MANUAL_AMOUNT_MISMATCH: 'Số tiền chuyển khoản phải khớp chính xác số tiền yêu cầu.',
  PAYOUT_MANUAL_REFERENCE_DUPLICATE: 'Mã giao dịch ngân hàng này đã được sử dụng.',
  PAYOUT_PROOF_INVALID: 'Chứng từ phải là PDF, PNG hoặc JPEG và không vượt quá 5 MB.',
  PAYOUT_PROOF_NOT_FOUND: 'Không tìm thấy chứng từ chuyển khoản.',
  PAYOUT_RETRY_NOT_ALLOWED: 'Trạng thái hiện tại không cho phép thử lại payout.',
};

export function getPayoutErrorMessage(error: unknown) {
  if (isAxiosError<ApiResponse<unknown>>(error)) {
    const messageCode = error.response?.data?.messageCode;
    const mapped = getPayoutMessageByCode(messageCode);
    if (mapped) {
      return mapped;
    }
  }
  return 'Không thể xử lý yêu cầu chi trả. Vui lòng thử lại.';
}

export function getPayoutMessageByCode(messageCode: string | null | undefined) {
  return messageCode ? PAYOUT_ERROR_MESSAGES[messageCode] : undefined;
}
