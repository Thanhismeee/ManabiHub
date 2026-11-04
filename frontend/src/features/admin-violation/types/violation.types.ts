export type ViolationReportStatus =
  | 'PENDING_REVIEW'
  | 'IN_REVIEW'
  | 'PENDING_EVIDENCE'
  | 'CORRECTION_REQUIRED'
  | 'RESOLVED_UPHELD'
  | 'RESOLVED_NO_VIOLATION'
  | 'INVALID'
  | 'CANCELLED';

export type ModerationDecisionType =
  | 'UPHELD'
  | 'DISMISSED'
  | 'PENDING_EVIDENCE'
  | 'CORRECTION_REQUIRED';

export type EvidenceRequestedFrom = 'REPORTER' | 'CREATOR' | 'BOTH';

export type ModerationActionType =
  | 'NONE'
  | 'FORCE_DRAFT'
  | 'REMOVE_CONTENT'
  | 'HIDE_COURSE'
  | 'BAN_ACCOUNT'
  | 'FREEZE_BALANCE';

export interface ViolationQueueItemResponse {
  reportId: string;
  status: ViolationReportStatus;
  targetType: string;
  targetId: string;
  reason: string;
  reporterName: string;
  submittedAt: string;
}

export interface ModerationHistoryItem {
  decisionId: string;
  decisionType: string;
  decisionNote: string;
  decidedAt: string;
  decidedBy: string;
  evidenceRequestedFrom?: EvidenceRequestedFrom;
  actions: string[];
}

export interface ReporterSummary {
  reporterId: string;
  displayName: string;
  role?: string;
  accountAge?: string;
}

export interface ViolationEvidence {
  evidenceId: string;
  evidenceType: 'LINK' | 'IMAGE' | 'DOCUMENT' | 'VIDEO';
  displayName: string;
  accessUrl: string;
  contentType?: string;
  submittedAt: string;
}

export interface ViolationTarget {
  targetType: string;
  targetId: string;
  courseId?: string;
  courseTitle?: string;
  currentStatus?: string;
  affectedTeacherProfileId?: string;
  affectedUserId?: string;
  affectedUserName?: string;
  contentTitle?: string;
}

export interface ViolationDetailResponse {
  reportId: string;
  status: ViolationReportStatus;
  reason: string;
  description?: string | null;
  submittedAt: string;
  reporter?: ReporterSummary;
  target: ViolationTarget;
  evidence: ViolationEvidence[];
  moderationHistory: ModerationHistoryItem[];
  previousWarnings: number;
  paidEnrollmentCount: number;
  availableActions: ModerationActionType[];
  severeActionAllowed: boolean;
}

export interface ResolveViolationRequest {
  decision: ModerationDecisionType;
  actions?: ModerationActionType[];
  decisionNote: string;
  evidenceRequestedFrom?: EvidenceRequestedFrom;
  targetIds?: string[];
}
