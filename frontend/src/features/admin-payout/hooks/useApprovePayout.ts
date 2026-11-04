import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'react-hot-toast';
import { adminPayoutService } from '../services/adminPayoutService';
import { getPayoutErrorMessage } from '../services/payoutError';
import type { MockPayoutScenario } from '../types/payout.types';

interface ApprovePayoutVariables {
  withdrawalRequestId: string;
  mockScenario?: MockPayoutScenario;
}

export function useApprovePayout() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ withdrawalRequestId, mockScenario }: ApprovePayoutVariables) =>
      mockScenario
        ? adminPayoutService.approvePayoutWithMockResult(
            withdrawalRequestId,
            mockScenario,
          )
        : adminPayoutService.approvePayout(withdrawalRequestId),
    onSuccess: () => {
      toast.success('Đã thực hiện chi trả và ghi nhận giao dịch thành công.');
    },
    onError: (error: unknown) => {
      toast.error(getPayoutErrorMessage(error));
    },
    onSettled: (_data, _error, variables) => {
      void queryClient.invalidateQueries({ queryKey: ['admin-payouts'] });
      void queryClient.invalidateQueries({
        queryKey: ['admin-payout', variables.withdrawalRequestId],
      });
    },
  });
}
