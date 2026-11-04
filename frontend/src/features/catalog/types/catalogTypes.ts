// ─── Public Course Summary (catalog list item) ───────────────────
import type { LearningLanguage, ProficiencyFramework } from '../../../shared/learning/languageProfiles';

export interface PublicCourseSummary {
  id: string;
  title: string;
  slug: string;
  thumbnailUrl?: string;
  jlptLevel?: string;
  learningLanguage?: LearningLanguage;
  proficiencyFramework?: ProficiencyFramework;
  frameworkVersion?: string;
  proficiencyLevel?: string;
  category?: string;
  price: number;
  currency: string;
  teacherId?: string;
  teacherName?: string;
  teacherAvatarUrl?: string;
  totalLessons: number;
  averageRating?: number;
  reviewCount?: number;
  enrollmentCount?: number;
  publishedAt?: string;
}

// ─── Catalog Filter Params ───────────────────────────────────────

export interface CourseCatalogFilters {
  keyword?: string;
  category?: string;
  learningLanguage?: LearningLanguage;
  proficiencyFramework?: ProficiencyFramework;
  proficiencyLevel?: string;
  jlptLevel?: string;
  frameworkVersion?: string;
  minPrice?: number;
  maxPrice?: number;
}

export type CourseCatalogParams = CourseCatalogFilters & {
  page?: number;
  size?: number;
  sort?: string;
};

// ─── Course Category (from /v1/course-categories) ────────────────

export interface CourseCategory {
  id: string;
  code: string;
  name: string;
  description?: string;
}
