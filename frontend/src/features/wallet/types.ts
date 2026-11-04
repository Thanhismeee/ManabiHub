export interface StudentWalletResponse {
  balance: number;
  frozenBalance: number;
  availableBalance: number;
  withdrawableBalance: number;
  availableWithdrawableBalance: number;
  currency: string;
  minimumPayoutAmount?: number;
}

export type StudentWithdrawalStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXECUTED'
  | 'FAILED'
  | 'CANCELLED';

export interface StudentWithdrawal {
  id: string;
  requestedAmount: number;
  currency: string;
  status: StudentWithdrawalStatus;
  bankName?: string;
  accountHolderName?: string;
  accountNumberMasked?: string;
  requestedAt: string;
  reviewedAt?: string;
  rejectionReason?: string;
}

export interface StudentBankAccount {
  id: string;
  bankCode: string;
  bankName: string;
  accountNumber: string;
  accountHolderName: string;
  branch?: string;
  isDefault: boolean;
  ownershipVerified: boolean;
}

export interface CreateStudentWithdrawalPayload {
  amount: number;
  bankAccountId?: string;
  bankAccount?: {
    bankCode: string;
    bankName: string;
    accountNumber: string;
    accountHolderName: string;
    branch?: string;
  };
  otpCode?: string;
  phoneAuthChallengeId?: string;
  firebaseIdToken?: string;
  saveAccount: boolean;
  ownershipConfirmed: boolean;
}

/** Business reason behind a ledger line (mirrors backend WalletTransactionType). */
export type WalletTransactionType =
  | 'TOP_UP'
  | 'PURCHASE'
  | 'REFUND'
  | 'GAME_REWARD'
  | 'ATTENDANCE_REWARD'
  | 'REVENUE_SHARE'
  | 'PAYOUT'
  | 'ADJUSTMENT'
  | 'ESCROW_HOLD'
  | 'ESCROW_RELEASE'
  | 'REVENUE_CREDITED'
  | 'REVENUE_CLEARED'
  | 'WITHDRAWAL_RESERVATION'
  | 'WITHDRAWAL_COMPLETED'
  | 'WITHDRAWAL_REJECTED'
  | 'WITHDRAWAL_CANCELLED'
  | 'ADMIN_ADJUSTMENT';

export type WalletDirection = 'IN' | 'OUT';

export interface WalletTransaction {
  id: string;
  transactionType: WalletTransactionType;
  direction: WalletDirection;
  amount: number;
  currency: string;
  referenceType: string | null;
  referenceId: string | null;
  referenceCode: string | null;
  note: string | null;
  createdAt: string;
}

export interface WalletTransactionRelatedRecord {
  kind: string;
  id: string;
  code: string | null;
  status: string | null;
  title: string | null;
  amount: number | null;
  occurredAt: string | null;
}

export interface WalletTransactionDetail extends WalletTransaction {
  relatedRecord: WalletTransactionRelatedRecord | null;
}

/** Query params accepted by the transaction-history endpoints. */
export interface WalletTransactionFilter {
  types?: WalletTransactionType[];
  direction?: WalletDirection;
  /** ISO date, e.g. 2026-08-01 */
  fromDate?: string;
  /** ISO date, e.g. 2026-08-31 */
  toDate?: string;
  referenceCode?: string;
  page?: number;
  size?: number;
}
