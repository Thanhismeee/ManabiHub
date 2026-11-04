import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { ApiResponse, PageResponse } from '../../../shared/types/api';
import type {
  TeacherWritingFeedbackPayload,
  WritingReviewFacets,
  WritingReviewOverview,
  WritingSubmissionStatus,
  WritingSubmissionDetail,
  WritingSubmissionSummary,
} from '../types/writingReviewTypes';

export interface WritingReviewListParams {
  page: number;
  size: number;
  query?: string;
  reviewed?: boolean;
  courseId?: string;
  lessonId?: string;
  status?: WritingSubmissionStatus;
}

export type WritingReviewOverviewParams = Pick<
  WritingReviewListParams,
  'query' | 'courseId' | 'lessonId' | 'status'
>;

export const writingReviewService = {
  async listSubmissions(
    params: WritingReviewListParams,
  ): Promise<PageResponse<WritingSubmissionSummary>> {
    const response = await axiosClient.get<
      ApiResponse<PageResponse<WritingSubmissionSummary>>
    >(ENDPOINTS.teacherWriting.submissions, { params });
    return response.data.data;
  },

  async getFacets(): Promise<WritingReviewFacets> {
    const response = await axiosClient.get<ApiResponse<WritingReviewFacets>>(
      `${ENDPOINTS.teacherWriting.submissions}/facets`,
    );
    return response.data.data;
  },

  async getOverview(params: WritingReviewOverviewParams): Promise<WritingReviewOverview> {
    const response = await axiosClient.get<ApiResponse<WritingReviewOverview>>(
      `${ENDPOINTS.teacherWriting.submissions}/overview`,
      { params },
    );
    return response.data.data;
  },

  async getSubmission(submissionId: string): Promise<WritingSubmissionDetail> {
    const response = await axiosClient.get<ApiResponse<WritingSubmissionDetail>>(
      ENDPOINTS.teacherWriting.detail(submissionId),
    );
    return response.data.data;
  },

  async saveFeedback(
    submissionId: string,
    payload: TeacherWritingFeedbackPayload,
  ): Promise<WritingSubmissionDetail> {
    const response = await axiosClient.put<ApiResponse<WritingSubmissionDetail>>(
      ENDPOINTS.teacherWriting.feedback(submissionId),
      payload,
    );
    return response.data.data;
  },
};
