export type DecisionDomain = 'KYC' | 'COURSE' | 'VIOLATION' | 'REFUND' | 'PAYOUT' | 'EXPENSE';
export type DecisionReviewStatus = 'UNREVIEWED' | 'REVIEWED' | 'WARNING_SENT';
export type DecisionWarningLevel = 'INFO' | 'WARNING' | 'HIGH';

export interface DecisionReviewSummary {
  auditLogId: string;
  domain: DecisionDomain;
  action: string;
  targetType: string;
  targetId?: string;
  decisionActorId: string;
  decisionActorName: string;
  decisionActorEmail: string;
  decisionRole: 'COURSE_MANAGER' | 'FINANCE_MANAGER';
  decisionAt: string;
  reviewStatus: DecisionReviewStatus;
  warningLevel?: DecisionWarningLevel;
  reviewedAt?: string;
}

export interface DecisionReviewDetail extends DecisionReviewSummary {
  beforeValue?: Record<string, unknown>;
  afterValue?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
  reviewNote?: string;
  reviewedBy?: string;
  warningSentAt?: string;
}

export interface DecisionReviewFilters {
  domain?: DecisionDomain | '';
  decisionRole?: '' | 'COURSE_MANAGER' | 'FINANCE_MANAGER';
  actor?: string;
  reviewStatus?: DecisionReviewStatus | '';
  warningLevel?: DecisionWarningLevel | '';
  from?: string;
  to?: string;
  page: number;
  size: number;
}
