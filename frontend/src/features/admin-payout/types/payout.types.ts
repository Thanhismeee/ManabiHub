export type PayoutStatus =
  | 'PROCESSING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'PENDING_RETRY'
  | 'REJECTED';

export type ReconciliationStatus =
  | 'MATCHED'
  | 'WARNING'
  | 'CRITICAL_MISMATCH'
  | 'RESOLVED';

export type WithdrawalStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXECUTED'
  | 'FAILED'
  | 'CANCELLED';

export type MockPayoutScenario =
  | 'SUCCESS'
  | 'RETRYABLE_FAILURE'
  | 'PERMANENT_FAILURE';

export type PayoutTransferMethod = 'GATEWAY' | 'MANUAL';
export type PayoutNotificationStatus = 'NOT_REQUIRED' | 'PENDING' | 'SENT' | 'FAILED';

export interface ReconciliationAlert {
  code: string;
  severity: 'WARNING' | 'CRITICAL';
  message: string;
}

export interface PayoutQueueItem {
  withdrawalRequestId: string;
  walletId: string;
  ownerType: 'TEACHER' | 'STUDENT';
  ownerId: string;
  ownerName: string;
  teacherId: string | null;
  teacherName: string;
  requestedAmount: number;
  bankName: string | null;
  accountNumberMasked: string | null;
  status: WithdrawalStatus;
  settlementStatus: PayoutStatus | null;
  reconciliationStatus: ReconciliationStatus;
  requestedAt: string;
  processingStartedAt: string | null;
  provider: string | null;
  providerReference: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  updatedAt: string | null;
  settlementUpdatedAt: string | null;
  retryCount: number;
}

export interface ReconciliationHistoryEntry {
  id: string;
  triggerType: 'DETAIL_REVIEW' | 'APPROVAL' | 'FINALIZATION' | 'MANUAL_TRANSFER';
  status: ReconciliationStatus;
  alerts: ReconciliationAlert[];
  checkedBy: string;
  createdAt: string;
}

export interface PayoutDetail {
  withdrawalRequestId: string;
  settlementId: string | null;
  ownerType: 'TEACHER' | 'STUDENT';
  ownerId: string;
  ownerName: string;
  ownerAccountStatus: string;
  teacherId: string | null;
  teacherName: string;
  teacherAccountStatus: string;
  requestedAmount: number;
  availableBalance: number;
  reservedBalance: number;
  pendingClearing: number;
  walletFrozen: boolean;
  escrowStatus: string;
  status: WithdrawalStatus;
  settlementStatus: PayoutStatus | null;
  reconciliationStatus: ReconciliationStatus;
  reconciliationAlerts: ReconciliationAlert[];
  bankName: string | null;
  bankBranch: string | null;
  accountHolderName: string | null;
  accountNumberMasked: string;
  requestedAt: string;
  processingStartedAt: string | null;
  settledAt: string | null;
  decision: string | null;
  decisionReason: string | null;
  gatewayProvider: string | null;
  gatewayReference: string | null;
  transferMethod: PayoutTransferMethod | null;
  manualProofAvailable: boolean;
  bankQrAvailable: boolean;
  manualProofOriginalName: string | null;
  manualProofSize: number | null;
  manualTransferredAt: string | null;
  failureCode: string | null;
  failureMessage: string | null;
  retryCount: number;
  notificationStatus: PayoutNotificationStatus;
  notificationAttempts: number;
  reconciliationHistory: ReconciliationHistoryEntry[];
}

export interface PayoutDecision {
  withdrawalRequestId: string;
  settlementId: string;
  withdrawalStatus: WithdrawalStatus;
  settlementStatus: PayoutStatus;
  reconciliationStatus: ReconciliationStatus;
  transferMethod: PayoutTransferMethod;
  gatewayReference: string | null;
  settledAt: string | null;
  notificationStatus: PayoutNotificationStatus;
}

export interface RejectPayoutPayload {
  reason: string;
}

export interface ManualTransferPayload {
  transactionReference: string;
  transferredAmount: number;
  transferredAt: string;
  note?: string;
}

export interface PayoutQueueParams {
  page: number;
  size: number;
  payoutId?: string;
  walletId?: string;
  status?: WithdrawalStatus;
  settlementStatus?: PayoutStatus;
  reconciliationStatus?: ReconciliationStatus;
  teacherKeyword?: string;
  provider?: string;
  providerReference?: string;
  minAmount?: string;
  maxAmount?: string;
  requestedFrom?: string;
  requestedTo?: string;
  sort?: string;
}
