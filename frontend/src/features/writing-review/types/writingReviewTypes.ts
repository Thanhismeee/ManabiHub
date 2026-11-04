export type WritingSubmissionStatus =
  | 'SUBMITTED'
  | 'SUGGESTION_PROCESSING'
  | 'SUGGESTION_READY'
  | 'SUGGESTION_FAILED'
  | 'TEACHER_FEEDBACK_READY';

export interface WritingSubmissionSummary {
  id: string;
  courseId: string;
  courseTitle: string;
  lessonId: string | null;
  lessonTitle: string;
  studentName: string;
  studentEmail: string;
  status: WritingSubmissionStatus;
  submittedAt: string;
  hasAiSuggestion: boolean;
  hasTeacherFeedback: boolean;
  score: number | null;
}

export interface AiWritingSuggestion {
  id: string;
  status: 'READY' | 'FAILED';
  grammarSuggestions: unknown;
  vocabularySuggestions: unknown;
  structureSuggestions: unknown;
  revisionGuidance: string | null;
  confidenceLevel: 'LOW' | 'MEDIUM' | 'HIGH' | null;
  official: false;
  failureReason: string | null;
  createdAt: string;
}

export interface TeacherWritingFeedback {
  id: string;
  score: number | null;
  comment: string;
  rubricResult: unknown;
  official: true;
  createdAt: string;
  updatedAt: string | null;
}

export interface WritingSubmissionDetail {
  id: string;
  courseId: string;
  courseTitle: string;
  lessonId: string | null;
  lessonTitle: string;
  studentName: string;
  studentEmail: string;
  content: string;
  status: WritingSubmissionStatus;
  submittedAt: string;
  aiSuggestion: AiWritingSuggestion | null;
  teacherFeedback: TeacherWritingFeedback | null;
}

export interface TeacherWritingFeedbackPayload {
  score: number;
  comment: string;
}

export interface WritingReviewLessonOption {
  id: string;
  title: string;
}

export interface WritingReviewCourseOption {
  id: string;
  title: string;
  lessons: WritingReviewLessonOption[];
}

export interface WritingReviewFacets {
  courses: WritingReviewCourseOption[];
}

export interface WritingReviewOverview {
  totalSubmissions: number;
  pendingSubmissions: number;
  reviewedSubmissions: number;
  averageScore: number | null;
}
