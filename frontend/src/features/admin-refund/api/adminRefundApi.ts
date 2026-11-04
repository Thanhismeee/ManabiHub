import { axiosClient } from '../../../shared/api/axiosClient';
import type { ApiResponse, PageResponse } from '../../../shared/types/api';
import type { RefundQueueResponse, RefundDetailResponse, RefundDecisionRequest, RefundQueueFilters } from '../types';
import { toExclusiveReportingRange } from '../../admin-finance/financeDisplay';

export const adminRefundApi = {
  getPendingRefunds: async (page: number, size: number, filters: RefundQueueFilters = {}): Promise<PageResponse<RefundQueueResponse>> => {
    const range = filters.createdFrom && filters.createdTo
      ? toExclusiveReportingRange(filters.createdFrom, filters.createdTo)
      : null;
    const response = await axiosClient.get<ApiResponse<PageResponse<RefundQueueResponse>>>('/v1/admin/refunds', {
      params: {
        ...filters,
        createdFrom: range?.from ?? (filters.createdFrom ? toExclusiveReportingRange(filters.createdFrom, filters.createdFrom).from : undefined),
        createdTo: range?.to ?? (filters.createdTo ? toExclusiveReportingRange(filters.createdTo, filters.createdTo).to : undefined),
        page,
        size,
        sort: 'createdAt,desc',
      }
    });
    return response.data.data;
  },

  getRefundDetail: async (id: string): Promise<RefundDetailResponse> => {
    const response = await axiosClient.get<ApiResponse<RefundDetailResponse>>(`/v1/admin/refunds/${id}`);
    return response.data.data;
  },

  approveRefund: async (id: string, payload: RefundDecisionRequest): Promise<void> => {
    await axiosClient.post(`/v1/admin/refunds/${id}/approve`, payload);
  },

  rejectRefund: async (id: string, payload: RefundDecisionRequest): Promise<void> => {
    await axiosClient.post(`/v1/admin/refunds/${id}/reject`, payload);
  }
};
