import type { PageResponse } from '../../../shared/types/api';

export interface CourseReview {
  id: string;
  rating: number;
  reviewText: string;
  authorDisplayName: string;
  authorAvatarUrl?: string;
  updatedAt: string;
  teacherReplyText?: string;
  teacherRepliedAt?: string;
}

export interface UpsertCourseReview {
  rating: number;
  reviewText: string;
}

export interface TeacherCourseReviewReply {
  replyText: string;
}

export type CourseReviewPage = PageResponse<CourseReview>;
