import { useQuery } from '@tanstack/react-query';
import { walletService } from '../services/walletService';

export const useTeacherWallet = () => {
  return useQuery({
    queryKey: ['teacher-wallet'],
    queryFn: async () => {
      const response = await walletService.getTeacherWallet();
      return response.data;
    },
  });
};
