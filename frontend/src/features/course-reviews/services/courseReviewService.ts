import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { ApiResponse } from '../../../shared/types/api';
import type {
  CourseReview,
  CourseReviewPage,
  TeacherCourseReviewReply,
  UpsertCourseReview,
} from '../types/courseReviewTypes';

export const courseReviewService = {
  getPublicReviews: async (
    courseIdentifier: string,
    page = 0,
    size = 10,
  ): Promise<CourseReviewPage> => {
    const response = await axiosClient.get<ApiResponse<CourseReviewPage>>(
      ENDPOINTS.publicCourses.reviews(courseIdentifier),
      { params: { page, size } },
    );
    return response.data.data;
  },

  getMyReview: async (courseId: string): Promise<CourseReview | null> => {
    const response = await axiosClient.get<ApiResponse<CourseReview | null>>(
      ENDPOINTS.student.courseReview(courseId),
    );
    return response.data.data;
  },

  upsertMyReview: async (
    courseId: string,
    payload: UpsertCourseReview,
  ): Promise<CourseReview> => {
    const response = await axiosClient.put<ApiResponse<CourseReview>>(
      ENDPOINTS.student.courseReview(courseId),
      payload,
    );
    return response.data.data;
  },

  replyToReview: async (
    reviewId: string,
    payload: TeacherCourseReviewReply,
  ): Promise<CourseReview> => {
    const response = await axiosClient.put<ApiResponse<CourseReview>>(
      ENDPOINTS.teacherCourseReviews.reply(reviewId),
      payload,
    );
    return response.data.data;
  },
};
