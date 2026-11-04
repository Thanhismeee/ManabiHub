import type { ModerationActionType } from '../types/violation.types';

export type EnforcementAction = Exclude<ModerationActionType, 'NONE'>;

export const moderationActionContent: Record<
  EnforcementAction,
  { label: string; consequence: string; severe: boolean }
> = {
  FORCE_DRAFT: {
    label: 'Buộc khóa học về bản nháp',
    consequence:
      'Khóa học biến mất khỏi danh mục và không nhận giao dịch mua mới.',
    severe: false,
  },
  HIDE_COURSE: {
    label: 'Ẩn khóa học',
    consequence: 'Khóa học bị ẩn ngay khỏi danh mục công khai.',
    severe: false,
  },
  REMOVE_CONTENT: {
    label: 'Ẩn nội dung vi phạm',
    consequence:
      'Nội dung được soft-remove, lịch sử học và mua hàng vẫn được giữ.',
    severe: false,
  },
  BAN_ACCOUNT: {
    label: 'Khóa tài khoản',
    consequence:
      'Tài khoản không thể đăng nhập hoặc sử dụng JWT hiện tại.',
    severe: true,
  },
  FREEZE_BALANCE: {
    label: 'Đóng băng ví',
    consequence:
      'Không thể tạo hoặc thực hiện payout; số dư không bị tịch thu.',
    severe: true,
  },
};
