export interface WishlistItem {
  id: string;
  addedAt: string;
  courseId: string;
  title: string;
  slug: string;
  thumbnailUrl?: string;
  jlptLevel?: string;
  category?: string;
  price: number;
  currency: string;
  teacherName?: string;
  totalLessons: number;
}
