export type RefundStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'APPROVED'
  | 'REJECTED'
  | 'RECONCILIATION_REQUIRED'
  | 'CANCELLED';

export type RefundProviderStatus =
  | 'NOT_REQUESTED'
  | 'PROCESSING'
  | 'SUCCESS'
  | 'FAILED'
  | 'PENDING'
  | 'UNAVAILABLE'
  | 'INVALID_RESULT';

export type RefundDecisionReasonCode =
  | 'STANDARD_ELIGIBLE'
  | 'DUPLICATE_CHARGE'
  | 'CONFIRMED_PAYMENT_ERROR'
  | 'PLATFORM_ACCESS_FAILURE'
  | 'OUTSIDE_REFUND_WINDOW'
  | 'PROGRESS_LIMIT_REACHED'
  | 'PROTECTED_CONTENT_CONSUMED'
  | 'PAYMENT_NOT_CONFIRMED'
  | 'DUPLICATE_REQUEST'
  | 'OTHER';

export type RefundMoneyValue = number | string;

interface RefundFinancialEvidence {
  orderItemId?: string | null;
  courseId?: string | null;
  courseTitle?: string | null;
  currency?: string | null;
  grossAmount?: RefundMoneyValue | null;
  commissionAmount?: RefundMoneyValue | null;
  teacherNetAmount?: RefundMoneyValue | null;
  paymentStatus?: string | null;
  paymentProvider?: string | null;
  paymentProviderTransactionId?: string | null;
  paymentAmount?: RefundMoneyValue | null;
  escrowStatus?: string | null;
  escrowAmount?: RefundMoneyValue | null;
  escrowReleaseAt?: string | null;
  providerStatus?: RefundProviderStatus | string | null;
  providerName?: string | null;
  providerReference?: string | null;
  providerResultCode?: string | null;
  providerAttemptCount?: number | null;
  reconciliationReasonCode?: string | null;
  decisionReasonCode?: RefundDecisionReasonCode | null;
  eligibilitySnapshot?: Record<string, unknown> | null;
}

export interface RefundQueueResponse extends RefundFinancialEvidence {
  id: string;
  orderId: string;
  orderCode: string;
  studentId: string;
  studentName: string;
  studentEmail: string;
  reason: string;
  status: RefundStatus;
  createdAt: string;
  decidedBy?: string | null;
  decidedAt?: string | null;
}

export interface RefundDetailResponse extends RefundFinancialEvidence {
  autoRefundAttemptCount?: number;
  autoRefundNextAttemptAt?: string | null;
  autoRefundLastErrorCode?: string | null;
  id: string;
  orderId: string;
  orderCode: string;
  studentId: string;
  studentName: string;
  studentEmail: string;
  status: RefundStatus;
  reason: string;
  decidedBy?: string;
  decisionNote?: string;
  decidedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface RefundDecisionRequest {
  reasonCode: RefundDecisionReasonCode;
  note: string;
}

export interface RefundQueueFilters {
  refundId?: string;
  status?: RefundStatus | '';
  providerStatus?: RefundProviderStatus | '';
  orderCode?: string;
  student?: string;
  course?: string;
  paymentProvider?: string;
  reconciliationRequired?: boolean | '';
  minAmount?: string;
  maxAmount?: string;
  createdFrom?: string;
  createdTo?: string;
}
