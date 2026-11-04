import type { LearningLanguage, ProficiencyFramework } from '../../../shared/learning/languageProfiles';

export interface PublicTeacherProfile {
  id: string;
  name: string;
  avatarUrl?: string;
  bio?: string;
  verified: boolean;
}
export interface PublicLessonBlock {
  id: string;
  title: string;
  type: 'VIDEO' | 'TEXT' | 'QUIZ' | 'FLASHCARD' | 'WRITING';
  durationMinutes?: number;
  orderIndex: number;
}

export interface PublicModule {
  id: string;
  title: string;
  orderIndex: number;
  blocks: PublicLessonBlock[];
}

export interface PublicCourseDetail {
  id: string;
  title: string;
  slug: string;
  description?: string;
  introduction?: string;
  jlptLevel?: string;
  learningLanguage?: LearningLanguage;
  proficiencyFramework?: ProficiencyFramework;
  frameworkVersion?: string;
  proficiencyLevel?: string;
  category?: string;
  thumbnailUrl?: string;
  outcomes?: string;
  price: number;
  currency: string;
  prerequisites?: string;
  targetStudents?: string;
  publishedAt?: string;
  aiSupported: boolean;
  accessDurationDays?: number;
  accessExpiresAt?: string | null;
  teacher: PublicTeacherProfile;
  isEnrolled: boolean;
  hasExpiredEnrollment?: boolean;
  isTeacherOwner?: boolean;
  totalDurationMinutes: number;
  totalLessons: number;
  averageRating?: number;
  reviewCount?: number;
  modules: PublicModule[];
}
