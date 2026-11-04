export interface CourseApproval {
  id: string;
  courseName: string;
  teacherName: string;
  teacherEmail: string;
  submittedAt: string;
  status: 'DRAFT' | 'PENDING' | 'APPROVED' | 'PUBLISHED' | 'REJECTED' | 'REQUEST_CORRECTION';
}

export interface CourseApprovalDetail extends CourseApproval {
  curriculumSummary: string;
  introduction?: string | null;
  jlptLevel?: string | null;
  category?: string | null;
  thumbnailUrl?: string | null;
  outcomes?: string | null;
  price?: number | null;
  currency?: string | null;
  prerequisites?: string | null;
  targetStudents?: string | null;
  moduleCount: number;
  lessonBlocksCount: number;
  totalVideoDurationMinutes: number;
  finalTestIncluded: boolean;
  policyEvidence?: string | null;
  previousDecisionReason?: string | null;
  teacherKycStatus?: string | null;
  teacherCanPublish: boolean;
  approvalReady: boolean;
  reviewDataAvailable?: boolean;
  learningGoals: string[];
  modules: CourseApprovalModule[];
  finalTest?: CourseApprovalFinalTest | null;
  validationErrors: CourseApprovalValidationError[];
  reviewCriteria: CourseApprovalCriterion[];
}

export interface CourseApprovalValidationError {
  code: string;
  message: string;
  severity: string;
}

export interface CourseApprovalCriterion {
  code: string;
  title: string;
  description: string;
  passed: boolean;
  reasons: string[];
}

export type CourseApprovalBlockType = 'TEXT' | 'VIDEO' | 'QUIZ' | 'FLASHCARD' | 'WRITING';

export interface CourseApprovalQuizItem {
  question: string;
  options: string[];
  answer: string;
}

export interface CourseApprovalFlashcard {
  front: string;
  back: string;
}

export interface CourseApprovalBlock {
  id: string;
  type: CourseApprovalBlockType;
  title: string;
  content?: string | null;
  videoUrl?: string | null;
  durationMinutes?: number | null;
  quizQuestion?: string | null;
  quizOptions: string[];
  quizAnswer?: string | null;
  quizItems: CourseApprovalQuizItem[];
  flashcards: CourseApprovalFlashcard[];
  writingPrompt?: string | null;
  rubric?: string | null;
  orderIndex: number;
  interactionRequiredAfter: boolean;
  interactionSatisfied: boolean;
  validationMessage?: string | null;
}

export interface CourseApprovalModule {
  id: string;
  title: string;
  description?: string | null;
  orderIndex: number;
  blocks: CourseApprovalBlock[];
}

export interface CourseApprovalFinalTestChoice {
  id?: string;
  content: string;
  isCorrect: boolean;
}

export interface CourseApprovalFinalTestQuestion {
  id?: string;
  content: string;
  explanation: string;
  choices: CourseApprovalFinalTestChoice[];
}

export interface CourseApprovalFinalTest {
  id: string;
  courseId: string;
  timeLimitMinutes: number;
  passingScore: number;
  maxRetakes: number;
  jlptLevel: string;
  skillFocus: string;
  questions: CourseApprovalFinalTestQuestion[];
}

export interface ReviewActionPayload {
  action: 'APPROVE' | 'REJECT' | 'REQUEST_CORRECTION';
  reason?: string;
}
