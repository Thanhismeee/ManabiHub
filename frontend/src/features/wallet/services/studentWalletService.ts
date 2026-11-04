import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { ApiResponse, PageResponse } from '../../../shared/types/api';
import type {
  CreateStudentWithdrawalPayload,
  StudentBankAccount,
  StudentWalletResponse,
  StudentWithdrawal,
  WalletTransaction,
  WalletTransactionDetail,
  WalletTransactionFilter,
} from '../types';
import { toTransactionParams } from './walletTransactionParams';
import type { PhoneOtpChallengeResponse } from '../../../shared/auth/firebasePhoneAuth';

/** Fetches the current student's money-wallet overview (balance). */
export async function getStudentWallet(): Promise<StudentWalletResponse> {
  const response = await axiosClient.get<ApiResponse<StudentWalletResponse>>(ENDPOINTS.student.wallet);
  return response.data.data;
}

/** UC-17: paginated + filterable wallet transaction history for the current student. */
export async function getStudentWalletTransactions(
  filter: WalletTransactionFilter = {},
): Promise<PageResponse<WalletTransaction>> {
  const response = await axiosClient.get<ApiResponse<PageResponse<WalletTransaction>>>(
    ENDPOINTS.student.walletTransactions,
    { params: toTransactionParams(filter) },
  );
  return response.data.data;
}

/** UC-17 flow 6a: detail of one transaction plus its related order/refund reference. */
export async function getStudentWalletTransactionDetail(
  transactionId: string,
): Promise<WalletTransactionDetail> {
  const response = await axiosClient.get<ApiResponse<WalletTransactionDetail>>(
    ENDPOINTS.student.walletTransactionDetail(transactionId),
  );
  return response.data.data;
}

export async function getStudentWithdrawals(page: number = 0, size: number = 10): Promise<PageResponse<StudentWithdrawal>> {
  const response = await axiosClient.get<ApiResponse<PageResponse<StudentWithdrawal>>>(
    ENDPOINTS.student.withdrawals,
    { params: { page, size, sort: 'requestedAt,desc' } },
  );
  return response.data.data;
}

export async function getStudentBankAccounts(): Promise<StudentBankAccount[]> {
  const response = await axiosClient.get<ApiResponse<StudentBankAccount[]>>(
    ENDPOINTS.student.withdrawalBankAccounts,
  );
  return response.data.data;
}

export async function sendStudentWithdrawalOtp(): Promise<PhoneOtpChallengeResponse> {
  const response = await axiosClient.post<ApiResponse<PhoneOtpChallengeResponse>>(
    ENDPOINTS.student.sendWithdrawalOtp,
  );
  return response.data.data;
}

export async function createStudentWithdrawal(
  payload: CreateStudentWithdrawalPayload,
): Promise<StudentWithdrawal> {
  const response = await axiosClient.post<ApiResponse<StudentWithdrawal>>(
    ENDPOINTS.student.withdrawals,
    payload,
  );
  return response.data.data;
}

export async function cancelStudentWithdrawal(id: string): Promise<StudentWithdrawal> {
  const response = await axiosClient.post<ApiResponse<StudentWithdrawal>>(
    ENDPOINTS.student.cancelWithdrawal(id),
  );
  return response.data.data;
}
