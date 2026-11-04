import { EXPENSE_CATEGORIES, type ExpenseCategory, type ExpenseSourceType, type ExpenseStatus } from './types';

export const EXPENSE_STATUS_LABELS: Record<ExpenseStatus, string> = {
  DRAFT: 'Chứng từ nháp',
  CONFIRMED: 'Đã duyệt, chưa thanh toán',
  PAID: 'Đã thanh toán',
  VOID: 'Đã vô hiệu',
};

export const EXPENSE_SOURCE_LABELS: Record<ExpenseSourceType, string> = {
  MANUAL_INVOICE: 'Hóa đơn nhập thủ công',
  IMPORTED_INVOICE: 'Hóa đơn nhập từ hệ thống khác',
  ADJUSTMENT: 'Bút toán điều chỉnh',
};

type ExpenseGroup = 'Hạ tầng' | 'AI / KYC' | 'Thanh toán' | 'Nhân sự' | 'Marketing' | 'Văn phòng' | 'Pháp lý / kế toán' | 'Khác';

interface ExpenseCategoryMeta {
  label: string;
  group: ExpenseGroup;
}

export const EXPENSE_CATEGORY_META = {
  INFRA_APP_COMPUTE: { label: 'Máy chủ ứng dụng', group: 'Hạ tầng' },
  INFRA_APP_DISK: { label: 'Ổ đĩa máy chủ ứng dụng', group: 'Hạ tầng' },
  INFRA_DATABASE: { label: 'Cơ sở dữ liệu', group: 'Hạ tầng' },
  INFRA_FRONTEND_HOSTING: { label: 'Lưu trữ frontend', group: 'Hạ tầng' },
  INFRA_API_GATEWAY: { label: 'API Gateway', group: 'Hạ tầng' },
  INFRA_OBJECT_STORAGE: { label: 'Lưu trữ tệp / đối tượng', group: 'Hạ tầng' },
  INFRA_CDN: { label: 'Mạng phân phối nội dung (CDN)', group: 'Hạ tầng' },
  INFRA_NETWORK: { label: 'Mạng và truyền dữ liệu', group: 'Hạ tầng' },
  INFRA_MONITORING: { label: 'Giám sát hạ tầng', group: 'Hạ tầng' },
  INFRA_BACKUP_DR: { label: 'Sao lưu và khôi phục thảm họa', group: 'Hạ tầng' },
  SMS_OTP: { label: 'SMS / OTP', group: 'AI / KYC' },
  AI_CHAT: { label: 'AI hội thoại', group: 'AI / KYC' },
  AI_WRITING: { label: 'AI hỗ trợ viết', group: 'AI / KYC' },
  KYC_IDENTITY: { label: 'Xác minh danh tính KYC', group: 'AI / KYC' },
  KYC_CERTIFICATE: { label: 'Xác minh chứng chỉ', group: 'AI / KYC' },
  EMAIL_TRANSACTIONAL: { label: 'Email giao dịch', group: 'AI / KYC' },
  BANK_ACCOUNT_VERIFY: { label: 'Xác minh tài khoản ngân hàng', group: 'AI / KYC' },
  EXTERNAL_STORAGE_SCAN: { label: 'Quét tệp lưu trữ ngoài', group: 'AI / KYC' },
  PAYMENT_GATEWAY_FEE: { label: 'Phí cổng thanh toán', group: 'Thanh toán' },
  PAYMENT_REFUND_FEE: { label: 'Phí hoàn tiền', group: 'Thanh toán' },
  PAYMENT_CHARGEBACK_FEE: { label: 'Phí chargeback', group: 'Thanh toán' },
  PAYOUT_TRANSFER_FEE: { label: 'Phí chuyển tiền chi trả', group: 'Thanh toán' },
  PAYOUT_RECONCILIATION_FEE: { label: 'Phí đối soát chi trả', group: 'Thanh toán' },
  CURRENCY_CONVERSION_FEE: { label: 'Phí chuyển đổi ngoại tệ', group: 'Thanh toán' },
  PROMOTION_GAME_REWARD: { label: 'Thưởng trò chơi', group: 'Marketing' },
  PROMOTION_ATTENDANCE_REWARD: { label: 'Thưởng chuyên cần', group: 'Marketing' },
  CUSTOMER_COMPENSATION: { label: 'Bồi thường khách hàng', group: 'Marketing' },
  PROMOTION_OTHER: { label: 'Khuyến mại khác', group: 'Marketing' },
  DOMAIN_DNS: { label: 'Tên miền và DNS', group: 'Hạ tầng' },
  TLS_CERTIFICATE: { label: 'Chứng thư TLS', group: 'Hạ tầng' },
  SECRET_MANAGEMENT: { label: 'Quản lý bí mật hệ thống', group: 'Hạ tầng' },
  SECURITY_WAF: { label: 'Tường lửa ứng dụng (WAF)', group: 'Hạ tầng' },
  SECURITY_SCANNING: { label: 'Quét bảo mật', group: 'Hạ tầng' },
  OBSERVABILITY_TOOL: { label: 'Công cụ quan sát hệ thống', group: 'Hạ tầng' },
  CI_CD: { label: 'CI / CD', group: 'Hạ tầng' },
  SOURCE_CONTROL: { label: 'Quản lý mã nguồn', group: 'Hạ tầng' },
  BACKUP_TOOL: { label: 'Công cụ sao lưu', group: 'Hạ tầng' },
  PERSONNEL_ENGINEERING: { label: 'Nhân sự kỹ thuật', group: 'Nhân sự' },
  PERSONNEL_CONTENT: { label: 'Nhân sự nội dung', group: 'Nhân sự' },
  PERSONNEL_FINANCE: { label: 'Nhân sự tài chính', group: 'Nhân sự' },
  PERSONNEL_SUPPORT: { label: 'Nhân sự hỗ trợ', group: 'Nhân sự' },
  CONTENT_PRODUCTION: { label: 'Sản xuất nội dung', group: 'Nhân sự' },
  MARKETING_ADS: { label: 'Quảng cáo', group: 'Marketing' },
  SALES_PARTNERSHIP: { label: 'Bán hàng và đối tác', group: 'Marketing' },
  LEGAL_COMPLIANCE: { label: 'Pháp lý và tuân thủ', group: 'Pháp lý / kế toán' },
  ACCOUNTING_AUDIT: { label: 'Kế toán và kiểm toán', group: 'Pháp lý / kế toán' },
  OFFICE_EQUIPMENT: { label: 'Thiết bị văn phòng', group: 'Văn phòng' },
  TRAINING_RECRUITMENT: { label: 'Đào tạo và tuyển dụng', group: 'Nhân sự' },
  CUSTOMER_SUPPORT_TOOL: { label: 'Công cụ hỗ trợ khách hàng', group: 'Văn phòng' },
  DESIGN_COLLABORATION_TOOL: { label: 'Công cụ thiết kế / cộng tác', group: 'Văn phòng' },
  OTHER_OPERATIONAL: { label: 'Chi phí vận hành khác', group: 'Khác' },
} satisfies Record<ExpenseCategory, ExpenseCategoryMeta>;

export const EXPENSE_CATEGORY_OPTIONS = EXPENSE_CATEGORIES.map((value) => ({
  value,
  ...EXPENSE_CATEGORY_META[value],
}));

export function expenseCategoryLabel(category: ExpenseCategory) {
  return EXPENSE_CATEGORY_META[category].label;
}
