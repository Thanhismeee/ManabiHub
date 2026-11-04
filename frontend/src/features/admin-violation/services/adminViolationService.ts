import { axiosClient } from '../../../shared/api/axiosClient';
import type { ApiResponse, PageResponse } from '../../../shared/types/api';
import type { ResolveViolationRequest, ViolationDetailResponse, ViolationQueueItemResponse } from '../types/violation.types';
import { ENDPOINTS } from '../../../shared/api/endpoints';

export const adminViolationService = {
  getViolationQueue: async (params?: { page?: number; size?: number; status?: string }) => {
    const response = await axiosClient.get<ApiResponse<PageResponse<ViolationQueueItemResponse>>>(
      ENDPOINTS.ADMIN_VIOLATIONS.QUEUE,
      { params }
    );
    return response.data.data;
  },

  getViolationDetail: async (id: string) => {
    const response = await axiosClient.get<ApiResponse<ViolationDetailResponse>>(
      ENDPOINTS.ADMIN_VIOLATIONS.DETAIL(id)
    );
    return response.data.data;
  },

  resolveViolation: async (id: string, data: ResolveViolationRequest) => {
    const response = await axiosClient.post<ApiResponse<ViolationDetailResponse>>(
      ENDPOINTS.ADMIN_VIOLATIONS.RESOLVE(id),
      data
    );
    return response.data.data;
  },

  downloadEvidence: async (accessUrl: string, fileName: string) => {
    if (/^https?:\/\//i.test(accessUrl)) {
      window.open(accessUrl, '_blank', 'noopener,noreferrer');
      return;
    }
    const response = await axiosClient.get<Blob>(accessUrl, { responseType: 'blob' });
    const objectUrl = URL.createObjectURL(response.data);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = fileName;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(objectUrl);
  },

  loadEvidencePreview: async (accessUrl: string) => {
    if (/^https?:\/\//i.test(accessUrl)) {
      return { url: accessUrl, shouldRevoke: false };
    }
    const response = await axiosClient.get<Blob>(accessUrl, { responseType: 'blob' });
    return { url: URL.createObjectURL(response.data), shouldRevoke: true };
  },
};
