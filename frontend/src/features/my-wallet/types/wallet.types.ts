export type TeacherWallet = {
  pendingBalance: number;
  availableBalance: number;
  reservedBalance?: number;
  walletFrozen: boolean;
  minimumPayoutAmount: number;
  clearingPeriodDays: number;
  nextPayoutDate?: string;
};

export type TeacherCourseRevenue = {
  courseId: string;
  courseTitle: string;
  purchaseCount: number;
  refundedCount: number;
  grossRevenue: number;
  teacherNetRevenue: number;
  heldAmount: number;
  releasedAmount: number;
  refundedAmount: number;
};

export type TeacherRevenueSummary = {
  totalGrossRevenue: number;
  totalTeacherNetRevenue: number;
  settledRevenue: number;
  heldInEscrow: number;
  availableInWallet: number;
  reservedForWithdrawal: number;
  totalWithdrawn: number;
  totalSales: number;
  totalRefundedSales: number;
  courseRevenue: TeacherCourseRevenue[];
};

export type WithdrawalStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXECUTED'
  | 'FAILED'
  | 'CANCELLED';

export type EscrowStatus =
  | 'HELD'
  | 'RELEASED'
  | 'REFUNDED'
  | 'CANCELLED';

export type EscrowLedgerItem = {
  id: string;
  orderId: string;
  courseName: string;
  grossAmount: number;
  platformCommissionAmount: number;
  teacherNetAmount: number;
  status: EscrowStatus;
  releaseAt: string;
  createdAt: string;
};

export type WithdrawalRequest = {
  id: string;
  requestedAmount: number;
  currency: string;
  status: WithdrawalStatus;
  bankCode: string;
  bankName: string;
  accountHolderName: string;
  accountNumberMasked: string;
  branch?: string;
  requestedAt: string;
  reviewedAt?: string;
  rejectionReason?: string;
};

export type BankAccountPayload = {
  bankCode: string;
  bankName: string;
  accountNumber: string;
  accountHolderName: string;
  branch?: string;
};

export type TeacherBankAccount = {
  id: string;
  bankCode: string;
  bankName: string;
  accountNumber: string;
  accountHolderName: string;
  branch?: string;
  isDefault: boolean;
};

export type CreateWithdrawalPayload = {
  amount: number;
  bankAccountId?: string;
  bankAccount?: BankAccountPayload;
  otpCode?: string;
  phoneAuthChallengeId?: string;
  firebaseIdToken?: string;
  saveAccount: boolean;
  bankQrDataUrl: string;
};
