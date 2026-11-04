export type MoneyValue = number | string;

export type RevenueGranularity = 'DAY' | 'WEEK' | 'MONTH';

export interface RevenueSummary {
  grossSales: MoneyValue;
  successfulOrders: number;
  refundAmount: MoneyValue;
  refundCount: number;
  refundRate: MoneyValue;
  netCollected: MoneyValue;
  commissionRecognized: MoneyValue;
  commissionReversed: MoneyValue;
  platformRevenue: MoneyValue;
  paymentFees: MoneyValue;
  operatingExpenses: MoneyValue;
  totalActualExpenses: MoneyValue;
  netOperatingResult: MoneyValue;
}

export interface RevenuePoint {
  bucket: string;
  grossSales: MoneyValue;
  successfulOrders: number;
  refundAmount: MoneyValue;
  refundCount: number;
  commissionRecognized: MoneyValue;
  commissionReversed: MoneyValue;
  platformRevenue: MoneyValue;
  paymentFees: MoneyValue;
  operatingExpenses: MoneyValue;
}

export interface RevenueDashboard {
  from: string;
  to: string;
  timezone: string;
  granularity: RevenueGranularity;
  generatedAt: string;
  summary: RevenueSummary;
  points: RevenuePoint[];
}

export type ExpenseStatus = 'DRAFT' | 'CONFIRMED' | 'PAID' | 'VOID';
export type ExpenseSourceType = 'MANUAL_INVOICE' | 'IMPORTED_INVOICE' | 'ADJUSTMENT';

export const EXPENSE_CATEGORIES = [
  'INFRA_APP_COMPUTE', 'INFRA_APP_DISK', 'INFRA_DATABASE', 'INFRA_FRONTEND_HOSTING',
  'INFRA_API_GATEWAY', 'INFRA_OBJECT_STORAGE', 'INFRA_CDN', 'INFRA_NETWORK',
  'INFRA_MONITORING', 'INFRA_BACKUP_DR', 'SMS_OTP', 'AI_CHAT', 'AI_WRITING',
  'KYC_IDENTITY', 'KYC_CERTIFICATE', 'EMAIL_TRANSACTIONAL', 'BANK_ACCOUNT_VERIFY',
  'EXTERNAL_STORAGE_SCAN', 'PAYMENT_GATEWAY_FEE', 'PAYMENT_REFUND_FEE',
  'PAYMENT_CHARGEBACK_FEE', 'PAYOUT_TRANSFER_FEE', 'PAYOUT_RECONCILIATION_FEE',
  'CURRENCY_CONVERSION_FEE', 'PROMOTION_GAME_REWARD', 'PROMOTION_ATTENDANCE_REWARD',
  'CUSTOMER_COMPENSATION', 'PROMOTION_OTHER', 'DOMAIN_DNS', 'TLS_CERTIFICATE',
  'SECRET_MANAGEMENT', 'SECURITY_WAF', 'SECURITY_SCANNING', 'OBSERVABILITY_TOOL',
  'CI_CD', 'SOURCE_CONTROL', 'BACKUP_TOOL', 'PERSONNEL_ENGINEERING', 'PERSONNEL_CONTENT',
  'PERSONNEL_FINANCE', 'PERSONNEL_SUPPORT', 'CONTENT_PRODUCTION', 'MARKETING_ADS',
  'SALES_PARTNERSHIP', 'LEGAL_COMPLIANCE', 'ACCOUNTING_AUDIT', 'OFFICE_EQUIPMENT',
  'TRAINING_RECRUITMENT', 'CUSTOMER_SUPPORT_TOOL', 'DESIGN_COLLABORATION_TOOL',
  'OTHER_OPERATIONAL',
] as const;

export type ExpenseCategory = typeof EXPENSE_CATEGORIES[number];

export interface ExpenseLinePayload {
  categoryCode: ExpenseCategory;
  description: string;
  originalAmount: MoneyValue;
}

export interface ExpensePayload {
  version?: number;
  vendorName: string;
  providerCode?: string;
  invoiceNumber?: string;
  description?: string;
  currency: string;
  exchangeRate: MoneyValue;
  incurredAt: string;
  dueDate?: string;
  exchangeRateDate?: string;
  exchangeRateSource?: string;
  billingPeriodFrom?: string;
  billingPeriodTo?: string;
  evidenceReference?: string;
  sourceType: ExpenseSourceType;
  lines: ExpenseLinePayload[];
}

export interface ExpenseSummary {
  id: string;
  expenseCode: string;
  vendorName: string;
  providerCode?: string;
  invoiceNumber?: string;
  currency: string;
  originalTotal: MoneyValue;
  totalAmountVnd: MoneyValue;
  incurredAt: string;
  dueDate?: string;
  status: ExpenseStatus;
  sourceType: ExpenseSourceType;
  lineCount: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface ExpenseDetail extends ExpenseSummary {
  description?: string;
  exchangeRate: MoneyValue;
  exchangeRateDate?: string;
  exchangeRateSource?: string;
  billingPeriodFrom?: string;
  billingPeriodTo?: string;
  paidAt?: string;
  paidBy?: string;
  evidenceReference?: string;
  createdBy: string;
  confirmedBy?: string;
  confirmedAt?: string;
  voidedBy?: string;
  voidedAt?: string;
  voidReason?: string;
  version: number;
  lines: Array<ExpenseLinePayload & { id: string; amountVnd: MoneyValue; lineOrder: number }>;
}

export interface ExpenseOverview {
  from: string;
  to: string;
  totalDocuments: number;
  totalConfirmedVnd: MoneyValue;
  draftCount: number;
  confirmedCount: number;
  paidCount: number;
  overdueCount: number;
  generatedAt: string;
}

export interface ExpenseFilters {
  status?: ExpenseStatus | '';
  category?: ExpenseCategory | '';
  keyword?: string;
  vendor?: string;
  providerCode?: string;
  invoiceNumber?: string;
  createdBy?: string;
  minAmountVnd?: string;
  maxAmountVnd?: string;
  incurredFrom?: string;
  incurredTo?: string;
  page: number;
  size: number;
}
