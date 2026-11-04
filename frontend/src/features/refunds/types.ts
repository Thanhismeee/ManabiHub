export type StudentRefundType =
  | 'STANDARD'
  | 'DISPUTE'
  | 'DUPLICATE_CHARGE'
  | 'PAYMENT_ERROR'
  | 'PLATFORM_ACCESS_FAILURE';

export type RefundStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'RECONCILIATION_REQUIRED'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED';

export interface RefundEligibilitySnapshot {
  snapshotVersion: string;
  policyVersion: string;
  refundType: StudentRefundType;
  paymentSucceededAt: string;
  requestedAt: string;
  timezone: string;
  elapsedCalendarDays: number;
  refundWindowDays: number;
  progressCompleted: number;
  progressTotal: number;
  measuredProgressPercent: number;
  progressThresholdPercent: number;
  protectedMaterialsFullyDownloaded: boolean;
  protectedMaterialsFullyDownloadedAt?: string | null;
  actuallyPaidAmount: number;
  currency: string;
  orderId: string;
  orderItemId: string;
  courseId: string;
  eligibilityResult: 'STANDARD_ELIGIBLE' | 'MANUAL_REVIEW_REQUIRED';
  reasonCodes: string[];
}

export interface StudentRefundResponse {
  id: string;
  orderId: string;
  orderCode: string;
  orderItemId: string;
  courseId: string;
  courseTitle: string;
  status: RefundStatus;
  refundType: StudentRefundType;
  reason: string;
  eligibilitySnapshot: RefundEligibilitySnapshot;
  decisionReasonCode?: string | null;
  decisionNote?: string | null;
  decidedAt?: string | null;
  cancellable: boolean;
  createdAt: string;
  updatedAt?: string | null;
  automaticRefundPending?: boolean;
  reconciliationReasonCode?: string | null;
}

export interface CreateStudentRefundRequest {
  orderItemId: string;
  refundType: StudentRefundType;
  reason: string;
}
