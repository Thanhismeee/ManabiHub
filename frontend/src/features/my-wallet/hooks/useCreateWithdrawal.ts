import { useMutation, useQueryClient } from '@tanstack/react-query';
import { walletService } from '../services/walletService';
import type { CreateWithdrawalPayload } from '../types/wallet.types';

export const useCreateWithdrawal = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateWithdrawalPayload) => walletService.createWithdrawal(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['teacher-wallet'] });
      queryClient.invalidateQueries({ queryKey: ['teacher-withdrawals'] });
    },
  });
};
