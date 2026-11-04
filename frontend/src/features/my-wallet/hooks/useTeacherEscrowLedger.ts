import { useQuery } from '@tanstack/react-query';
import { walletService } from '../services/walletService';

interface UseTeacherEscrowLedgerParams {
  page?: number;
  size?: number;
}

export function useTeacherEscrowLedger(params?: UseTeacherEscrowLedgerParams) {
  return useQuery({
    queryKey: ['teacher-escrow-ledger', params],
    queryFn: async () => {
      const response = await walletService.getTeacherEscrowLedger(params);
      return response.data;
    },
  });
}
