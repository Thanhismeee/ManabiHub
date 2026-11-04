import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { PublicCourseDetail } from '../types/courseDetailTypes';
import type {
  CourseCatalogParams,
  CourseCategory,
  PublicCourseSummary,
} from '../types/catalogTypes';
import type { ApiResponse, PageResponse } from '../../../shared/types/api';

export const catalogService = {
  getCourseDetail: async (id: string): Promise<PublicCourseDetail> => {
    const response = await axiosClient.get(`/v1/public/courses/${id}`);
    return response.data.data;
  },

  searchCourses: async (
    params: CourseCatalogParams,
  ): Promise<PageResponse<PublicCourseSummary>> => {
    // Strip undefined values so they don't appear as "undefined" in query string
    const cleanParams = Object.fromEntries(
      Object.entries(params).filter(([, v]) => v !== undefined && v !== ''),
    );
    const response = await axiosClient.get<
      ApiResponse<PageResponse<PublicCourseSummary>>
    >(ENDPOINTS.publicCourses.list, { params: cleanParams });
    return response.data.data;
  },

  getCategories: async (): Promise<CourseCategory[]> => {
    const response = await axiosClient.get<ApiResponse<CourseCategory[]>>(
      ENDPOINTS.courseCategories.list,
    );
    return response.data.data;
  },
};
