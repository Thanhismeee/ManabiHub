import { axiosClient } from '../../shared/api/axiosClient';
import type { ApiResponse, PageResponse } from '../../shared/types/api';
import type {
  DecisionReviewDetail,
  DecisionReviewFilters,
  DecisionReviewSummary,
  DecisionWarningLevel,
} from './types';

const BASE = '/v1/admin/decision-reviews';

export const adminDecisionReviewApi = {
  search: async (filters: DecisionReviewFilters): Promise<PageResponse<DecisionReviewSummary>> => {
    const response = await axiosClient.get<ApiResponse<PageResponse<DecisionReviewSummary>>>(BASE, {
      params: filters,
    });
    return response.data.data;
  },
  get: async (auditLogId: string): Promise<DecisionReviewDetail> => {
    const response = await axiosClient.get<ApiResponse<DecisionReviewDetail>>(`${BASE}/${auditLogId}`);
    return response.data.data;
  },
  markReviewed: async (auditLogId: string): Promise<DecisionReviewDetail> => {
    const response = await axiosClient.post<ApiResponse<DecisionReviewDetail>>(`${BASE}/${auditLogId}/reviewed`);
    return response.data.data;
  },
  sendWarning: async (
    auditLogId: string,
    payload: { level: DecisionWarningLevel; note: string },
  ): Promise<DecisionReviewDetail> => {
    const response = await axiosClient.post<ApiResponse<DecisionReviewDetail>>(`${BASE}/${auditLogId}/warnings`, payload);
    return response.data.data;
  },
};
