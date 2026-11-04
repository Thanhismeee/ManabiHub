import { useQuery } from '@tanstack/react-query';
import { walletService } from '../services/walletService';
import type { WalletTransactionFilter } from '../../wallet/types';

/** Paginated + filtered revenue-wallet history (UC-17 steps 3, 6, 7). */
export function useTeacherWalletTransactions(filter: WalletTransactionFilter) {
  return useQuery({
    queryKey: ['teacher-wallet-transactions', filter],
    queryFn: async () => {
      const response = await walletService.getTeacherTransactions(filter);
      return response.data;
    },
    placeholderData: (previous) => previous,
  });
}

/** Transaction detail, fetched only once a row is opened (UC-17 flow 6a). */
export function useTeacherWalletTransactionDetail(transactionId: string | null) {
  return useQuery({
    queryKey: ['teacher-wallet-transaction', transactionId],
    queryFn: async () => {
      const response = await walletService.getTeacherTransactionDetail(transactionId as string);
      return response.data;
    },
    enabled: Boolean(transactionId),
  });
}
