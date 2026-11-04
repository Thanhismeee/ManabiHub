import { useQuery } from '@tanstack/react-query';
import {
  getStudentWalletTransactionDetail,
  getStudentWalletTransactions,
} from '../services/studentWalletService';
import type { WalletTransactionFilter } from '../types';

/** Paginated + filtered wallet history (UC-17 steps 3, 6, 7). */
export function useStudentWalletTransactions(filter: WalletTransactionFilter) {
  return useQuery({
    queryKey: ['student-wallet-transactions', filter],
    queryFn: () => getStudentWalletTransactions(filter),
    placeholderData: (previous) => previous,
  });
}

/** Transaction detail, fetched only once a row is opened (UC-17 flow 6a). */
export function useStudentWalletTransactionDetail(transactionId: string | null) {
  return useQuery({
    queryKey: ['student-wallet-transaction', transactionId],
    queryFn: () => getStudentWalletTransactionDetail(transactionId as string),
    enabled: Boolean(transactionId),
  });
}
