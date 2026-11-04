export interface PublicTeacherCourse {
  id: string;
  title: string;
  slug: string;
  thumbnailUrl?: string;
  jlptLevel?: string;
  category?: string;
  price: number;
  currency: string;
  totalLessons: number;
  publishedAt?: string;
  averageRating?: number;
  reviewCount?: number;
}

export interface PublicTeacherSummary {
  id: string;
  displayName: string;
  avatarUrl?: string;
  bio?: string;
  verified: boolean;
  publishedCourseCount: number;
}

export interface PublicTeacherProfile extends PublicTeacherSummary {
  courses: PublicTeacherCourse[];
}
