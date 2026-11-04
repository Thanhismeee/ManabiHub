import { axiosClient } from '../../../shared/api/axiosClient';

export interface TeacherCourseAnalyticsResponse {
  totalEnrollment: number;
  activeLearners: number;
  completedLearners: number;
  completionRate: number;
  grossRevenue: number;
  netRevenue: number;
  refundRate: number;
  averageRating: number | null;
  totalReviews: number;
}

export async function fetchCourseAnalytics(courseId: string, startDate?: string, endDate?: string): Promise<TeacherCourseAnalyticsResponse> {
  const params = new URLSearchParams();
  if (startDate) params.append('startDate', startDate);
  if (endDate) params.append('endDate', endDate);
  
  const url = `/v1/teacher/courses/${courseId}/analytics${params.toString() ? '?' + params.toString() : ''}`;
  const response = await axiosClient.get<{ data: TeacherCourseAnalyticsResponse }>(url);
  return response.data.data;
}
