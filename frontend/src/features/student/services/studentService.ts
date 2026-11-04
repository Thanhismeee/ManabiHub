import { axiosClient as api } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { ApiResponse, PageResponse } from '../../../shared/types/api';
import type { StudentCourseSummary, StudentDashboardStats } from '../types/studentTypes';

export const studentService = {
  getDashboardStats: async (): Promise<StudentDashboardStats> => {
    const response = await api.get<ApiResponse<StudentDashboardStats>>(
      ENDPOINTS.student.dashboardStats,
    );
    return response.data.data;
  },

  getEnrolledCourses: async (params: { page: number; size: number }): Promise<PageResponse<StudentCourseSummary>> => {
    const response = await api.get<ApiResponse<PageResponse<StudentCourseSummary>>>(
      ENDPOINTS.student.courses,
      { params },
    );
    return response.data.data;
  },
};
