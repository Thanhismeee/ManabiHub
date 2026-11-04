import { useQuery } from '@tanstack/react-query';
import { walletService } from '../services/walletService';

export const useTeacherWithdrawals = (params?: any) => {
  return useQuery({
    queryKey: ['teacher-withdrawals', params],
    queryFn: async () => {
      const response = await walletService.getTeacherWithdrawals(params);
      return response.data;
    },
  });
};
