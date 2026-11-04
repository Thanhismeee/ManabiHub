import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { ApiResponse, PageResponse } from '../../../shared/types/api';
import type {
  PayoutDecision,
  PayoutDetail,
  PayoutQueueItem,
  PayoutQueueParams,
  RejectPayoutPayload,
  ManualTransferPayload,
  MockPayoutScenario,
} from '../types/payout.types';

export const adminPayoutService = {
  async getPayoutQueue(params: PayoutQueueParams) {
    const response = await axiosClient.get<ApiResponse<PageResponse<PayoutQueueItem>>>(
      ENDPOINTS.ADMIN_PAYOUT.QUEUE,
      { params },
    );
    return response.data.data;
  },

  async getPayoutDetail(withdrawalRequestId: string) {
    const response = await axiosClient.get<ApiResponse<PayoutDetail>>(
      ENDPOINTS.ADMIN_PAYOUT.DETAIL(withdrawalRequestId),
    );
    return response.data.data;
  },

  async reviewReconciliation(withdrawalRequestId: string) {
    const response = await axiosClient.post<ApiResponse<PayoutDetail>>(
      ENDPOINTS.ADMIN_PAYOUT.RECONCILE(withdrawalRequestId),
    );
    return response.data.data;
  },

  async approvePayout(withdrawalRequestId: string) {
    const response = await axiosClient.post<ApiResponse<PayoutDecision>>(
      ENDPOINTS.ADMIN_PAYOUT.APPROVE(withdrawalRequestId),
    );
    return response.data.data;
  },

  async approvePayoutWithMockResult(
    withdrawalRequestId: string,
    scenario: MockPayoutScenario,
  ) {
    const response = await axiosClient.post<ApiResponse<PayoutDecision>>(
      ENDPOINTS.ADMIN_PAYOUT.MOCK_APPROVE(withdrawalRequestId),
      { scenario },
    );
    return response.data.data;
  },

  async retryPayout(withdrawalRequestId: string) {
    const response = await axiosClient.post<ApiResponse<PayoutDecision>>(
      ENDPOINTS.ADMIN_PAYOUT.RETRY(withdrawalRequestId),
    );
    return response.data.data;
  },

  async confirmManualTransfer(
    withdrawalRequestId: string,
    payload: ManualTransferPayload,
    proof: File,
  ) {
    const formData = new FormData();
    formData.append(
      'metadata',
      new Blob([JSON.stringify(payload)], { type: 'application/json' }),
    );
    formData.append('proof', proof);
    const response = await axiosClient.post<ApiResponse<PayoutDecision>>(
      ENDPOINTS.ADMIN_PAYOUT.MANUAL_TRANSFER(withdrawalRequestId),
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      },
    );
    return response.data.data;
  },

  async downloadManualProof(withdrawalRequestId: string) {
    const response = await axiosClient.get<Blob>(
      ENDPOINTS.ADMIN_PAYOUT.MANUAL_PROOF(withdrawalRequestId),
      { responseType: 'blob' },
    );
    return response.data;
  },

  async downloadBankQr(withdrawalRequestId: string) {
    const response = await axiosClient.get<Blob>(
      ENDPOINTS.ADMIN_PAYOUT.BANK_QR(withdrawalRequestId),
      { responseType: 'blob' },
    );
    return response.data;
  },

  async rejectPayout(withdrawalRequestId: string, payload: RejectPayoutPayload) {
    const response = await axiosClient.post<ApiResponse<PayoutDecision>>(
      ENDPOINTS.ADMIN_PAYOUT.REJECT(withdrawalRequestId),
      payload,
    );
    return response.data.data;
  },
};
