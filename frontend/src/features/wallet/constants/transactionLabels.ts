import type { WalletTransactionType } from '../types';

/** Vietnamese labels for wallet ledger types (UC-17 transaction history). */
export const TRANSACTION_TYPE_LABELS: Record<WalletTransactionType, string> = {
  TOP_UP: 'Nạp tiền vào ví',
  PURCHASE: 'Thanh toán khóa học',
  REFUND: 'Hoàn tiền',
  GAME_REWARD: 'Thưởng trò chơi',
  ATTENDANCE_REWARD: 'Thưởng điểm danh hằng ngày',
  REVENUE_SHARE: 'Chia sẻ doanh thu',
  PAYOUT: 'Chi trả',
  ADJUSTMENT: 'Điều chỉnh',
  ESCROW_HOLD: 'Tạm giữ doanh thu',
  ESCROW_RELEASE: 'Giải phóng doanh thu',
  REVENUE_CREDITED: 'Ghi nhận doanh thu',
  REVENUE_CLEARED: 'Doanh thu đã đối soát',
  WITHDRAWAL_RESERVATION: 'Giữ tiền chờ rút',
  WITHDRAWAL_COMPLETED: 'Rút tiền thành công',
  WITHDRAWAL_REJECTED: 'Rút tiền bị từ chối',
  WITHDRAWAL_CANCELLED: 'Rút tiền đã hủy',
  ADMIN_ADJUSTMENT: 'Điều chỉnh bởi quản trị viên',
};

/** Types a Student can see in their money wallet. */
export const STUDENT_TRANSACTION_TYPES: WalletTransactionType[] = [
  'TOP_UP',
  'PURCHASE',
  'REFUND',
  'GAME_REWARD',
  'ATTENDANCE_REWARD',
  'ADJUSTMENT',
  'ADMIN_ADJUSTMENT',
];

/** Types a Teacher can see in their revenue wallet. */
export const TEACHER_TRANSACTION_TYPES: WalletTransactionType[] = [
  'ESCROW_HOLD',
  'ESCROW_RELEASE',
  'REVENUE_CREDITED',
  'REVENUE_CLEARED',
  'WITHDRAWAL_RESERVATION',
  'WITHDRAWAL_COMPLETED',
  'WITHDRAWAL_REJECTED',
  'WITHDRAWAL_CANCELLED',
  'ADMIN_ADJUSTMENT',
];

export const REFERENCE_TYPE_LABELS: Record<string, string> = {
  ORDER: 'Đơn hàng',
  WALLET_TOPUP: 'Đơn nạp ví',
  ESCROW: 'Bản ghi tạm giữ',
  WITHDRAWAL_REQUEST: 'Yêu cầu rút tiền',
};

export function transactionTypeLabel(type: WalletTransactionType): string {
  return TRANSACTION_TYPE_LABELS[type] ?? type;
}

export function referenceTypeLabel(referenceType: string | null): string {
  if (!referenceType) return '—';
  return REFERENCE_TYPE_LABELS[referenceType] ?? referenceType;
}

export function formatWalletDateTime(value: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}
