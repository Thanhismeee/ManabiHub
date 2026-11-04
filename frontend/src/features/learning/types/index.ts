export type LessonProgressStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';

export type LessonBlockType = 'VIDEO' | 'TEXT' | 'QUIZ' | 'FLASHCARD' | 'WRITING';

export interface QuizQuestion {
  question: string;
  options: string[];
}

export interface QuizSubmissionResult {
  score: number;
  passed: boolean;
  correctCount: number;
  totalQuestions: number;
  progressStatus: LessonProgressStatus;
  feedback: Array<{
    questionIndex: number;
    correct: boolean;
    correctAnswer: string;
  }>;
}

export interface FinalTestEligibility {
  configured: boolean;
  eligible: boolean;
  reason?: 'FINAL_TEST_NOT_CONFIGURED' | 'FINAL_TEST_ALREADY_PASSED' | 'LESSONS_INCOMPLETE' | 'ATTEMPTS_EXHAUSTED';
  finalTestId?: string;
  totalLessons: number;
  completedLessons: number;
  attemptsUsed: number;
  attemptsAllowed: number;
  passed: boolean;
}

export interface FinalTestAttempt {
  attemptId: string;
  timeLimitMinutes: number;
  passingScore: number;
  attemptsRemaining: number;
  startedAt: string;
  expiresAt: string;
  questions: Array<{
    id: string;
    content: string;
    choices: Array<{
      id: string;
      content: string;
    }>;
  }>;
}

export interface FinalTestSubmissionResult {
  attemptId: string;
  score: number;
  passed: boolean;
  certificateBlocked: boolean;
  correctCount: number;
  totalQuestions: number;
  feedback: Array<{
    questionId: string;
    correct: boolean;
    explanation: string;
    correctChoiceIds: string[];
  }>;
}

export interface FlashcardItem {
  front: string;
  back: string;
}

export interface LearningLessonBlock {
  id: string;
  moduleId: string;
  type: LessonBlockType;
  title: string;
  content?: string;
  videoUrl?: string;
  durationMinutes?: number;
  quizQuestion?: string;
  quizOptions: string[];
  quizItems: QuizQuestion[];
  flashcards: FlashcardItem[];
  flashcardStatuses?: ('REMEMBERED' | 'NEEDS_REVIEW' | null)[];
  writingPrompt?: string;
  rubric?: string;
  orderIndex: number;
  contentAvailable: boolean;
  progressStatus: LessonProgressStatus;
  lastVideoPositionSeconds?: number;
  watchedVideoSeconds?: number;
  completedAt?: string;
  current: boolean;
  locked: boolean;
}

export interface LearningModule {
  id: string;
  title: string;
  orderIndex: number;
  blocks: LearningLessonBlock[];
}

export interface CourseLearning {
  courseId: string;
  courseTitle: string;
  enrollmentId: string;
  modules: LearningModule[];
  currentLessonBlockId: string | null;
  totalLessons: number;
  completedLessons: number;
  progressPercent: number;
  courseCompleted: boolean;
  warnings: string[];
  accessStatus?: 'ACTIVE' | 'REFUND_PENDING' | 'REFUNDED' | 'REVOKED' | 'COMPLETED' | 'EXPIRED';
  expiresAt?: string | null;
}

export interface CertificateEligibility {
  eligible: boolean;
  progressComplete: boolean;
  requiredAssignmentsComplete: boolean;
  exerciseScoreSatisfied: boolean;
  exerciseAverageScore: number | null;
  exerciseScoreThreshold: number;
  finalTestPassed: boolean;
  reasons: string[];
}

export interface LearningCertificate {
  id: string;
  enrollmentId: string;
  courseId: string;
  certificateNumber: string;
  studentName: string;
  courseTitle: string;
  issuedAt: string | number;
  completedAt: string | number | null;
}

export interface CourseProgressSummary {
  courseId: string;
  courseTitle: string;
  totalLessons: number;
  completedLessons: number;
  progressPercent: number;
  nextLessonBlockId: string | null;
  nextLessonTitle: string | null;
  courseCompleted: boolean;
  finalTestEligibility: FinalTestEligibility;
  certificateEligibility: CertificateEligibility;
}

export interface LessonProgress {
  lessonBlockId: string;
  enrollmentId: string;
  status: LessonProgressStatus;
  lastVideoPositionSeconds?: number;
  watchedVideoSeconds?: number;
  completedAt?: string;
  updatedAt?: string;
}

export interface GrammarSuggestion {
  error: string;
  correction: string;
  explanation: string;
}

export interface VocabularySuggestion {
  word: string;
  suggestion: string;
  explanation: string;
}

export interface StructureSuggestion {
  issue: string;
  suggestion: string;
}

export interface AiWritingSuggestionResponse {
  id: string;
  status: 'READY' | 'FAILED';
  grammarSuggestions: GrammarSuggestion[];
  vocabularySuggestions: VocabularySuggestion[];
  structureSuggestions: StructureSuggestion[];
  revisionGuidance?: string;
  official: false;
  failureReason?: string;
  createdAt: string;
}

export interface TeacherWritingFeedback {
  id: string;
  score?: number;
  comment?: string;
  rubricResult?: Record<string, unknown>;
  official: true;
  createdAt: string;
  updatedAt?: string;
}

export interface WritingSubmissionDetail {
  id: string;
  lessonBlockId: string;
  content: string;
  status: 'DRAFT' | 'SUBMITTED' | 'SUGGESTION_PROCESSING' | 'SUGGESTION_READY' | 'SUGGESTION_FAILED' | 'TEACHER_FEEDBACK_READY';
  submittedAt: string;
  aiSuggestion?: AiWritingSuggestionResponse;
  teacherFeedback?: TeacherWritingFeedback;
}
