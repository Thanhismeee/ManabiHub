export interface CommercialPolicy {
  currency: string;
  commissionRate: number;
  refundWindowDays: number;
  refundProgressLimitPercent: number;
  escrowHoldingDays: number;
  payoutThreshold: number;
  withdrawalFee: number;
  kycTargetDaysMin: number;
  kycTargetDaysMax: number;
  policyVersion: string;
  effectiveAt: string;
}

export type HelpAudience = 'all' | 'instructor' | 'learner';

export type HelpCategory =
  | 'instructors'
  | 'learners'
  | 'trust-safety'
  | 'ai-and-data'
  | 'legal';

export type HelpArticleStatus = 'provisional' | 'draft';

export interface HelpArticleMetadata {
  id: string;
  path: string;
  title: string;
  summary: string;
  category: HelpCategory;
  audience: HelpAudience;
  keywords: string[];
  relatedPaths: string[];
  lastReviewedAt: string;
  policyVersion: string;
  status: HelpArticleStatus;
  discoverable: boolean;
}

export interface HelpCategoryDefinition {
  id: HelpCategory;
  label: string;
  description: string;
}
