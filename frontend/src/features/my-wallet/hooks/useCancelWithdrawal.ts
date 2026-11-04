import { useMutation, useQueryClient } from '@tanstack/react-query';
import { walletService } from '../services/walletService';

export function useCancelWithdrawal() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => walletService.cancelWithdrawal(id),
    onSuccess: () => {
      // Keep the wallet metrics and history in sync immediately after cancellation.
      queryClient.invalidateQueries({ queryKey: ['teacher-wallet'] });
      queryClient.invalidateQueries({ queryKey: ['teacher-withdrawals'] });
    },
  });
}
