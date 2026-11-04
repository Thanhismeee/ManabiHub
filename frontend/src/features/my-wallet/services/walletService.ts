import { axiosClient } from '../../../shared/api/axiosClient';
import type { ApiResponse, PageResponse } from '../../../shared/types/api';
import type {
  TeacherWallet,
  TeacherRevenueSummary,
  WithdrawalRequest,
  CreateWithdrawalPayload,
} from '../types/wallet.types';
import type {
  WalletTransaction,
  WalletTransactionDetail,
  WalletTransactionFilter,
} from '../../wallet/types';
import { toTransactionParams } from '../../wallet/services/walletTransactionParams';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { PhoneOtpChallengeResponse } from '../../../shared/auth/firebasePhoneAuth';

export const walletService = {
  getTeacherWallet: async (): Promise<ApiResponse<TeacherWallet>> => {
    const response = await axiosClient.get(ENDPOINTS.teacherWallet.detail);
    return response.data;
  },

  getTeacherRevenueSummary: async (): Promise<ApiResponse<TeacherRevenueSummary>> => {
    const response = await axiosClient.get(ENDPOINTS.teacherWallet.revenueSummary);
    return response.data;
  },

  getTeacherWithdrawals: async (params?: any): Promise<ApiResponse<PageResponse<WithdrawalRequest>>> => {
    const response = await axiosClient.get(ENDPOINTS.teacherWallet.withdrawals, { params });
    return response.data;
  },

  getTeacherEscrowLedger: async (params?: any): Promise<ApiResponse<PageResponse<import('../types/wallet.types').EscrowLedgerItem>>> => {
    const response = await axiosClient.get(ENDPOINTS.teacherWallet.escrow, { params });
    return response.data;
  },

  /** UC-17: paginated + filterable revenue-wallet transaction history. */
  getTeacherTransactions: async (
    filter: WalletTransactionFilter = {},
  ): Promise<ApiResponse<PageResponse<WalletTransaction>>> => {
    const response = await axiosClient.get(ENDPOINTS.teacherWallet.transactions, {
      params: toTransactionParams(filter),
    });
    return response.data;
  },

  /** UC-17 flow 6a: detail of one revenue-wallet transaction. */
  getTeacherTransactionDetail: async (
    transactionId: string,
  ): Promise<ApiResponse<WalletTransactionDetail>> => {
    const response = await axiosClient.get(ENDPOINTS.teacherWallet.transactionDetail(transactionId));
    return response.data;
  },

  getWithdrawalDetail: async (id: string): Promise<ApiResponse<WithdrawalRequest>> => {
    const response = await axiosClient.get(ENDPOINTS.teacherWallet.withdrawalDetail(id));
    return response.data;
  },

  createWithdrawal: async (payload: CreateWithdrawalPayload): Promise<ApiResponse<WithdrawalRequest>> => {
    const response = await axiosClient.post(ENDPOINTS.teacherWallet.withdrawals, payload);
    return response.data;
  },

  cancelWithdrawal: async (id: string): Promise<ApiResponse<void>> => {
    const response = await axiosClient.put(ENDPOINTS.teacherWallet.cancelWithdrawal(id));
    return response.data;
  },

  sendWithdrawalOtp: async (): Promise<ApiResponse<PhoneOtpChallengeResponse>> => {
    const response = await axiosClient.post<ApiResponse<PhoneOtpChallengeResponse>>(
      ENDPOINTS.teacherWallet.sendWithdrawalOtp,
    );
    return response.data;
  },

  getSavedBankAccounts: async (): Promise<ApiResponse<import('../types/wallet.types').TeacherBankAccount[]>> => {
    const response = await axiosClient.get(ENDPOINTS.teacherWallet.bankAccounts);
    return response.data;
  },
};
