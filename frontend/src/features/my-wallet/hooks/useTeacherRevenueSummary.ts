import { useQuery } from '@tanstack/react-query';
import { walletService } from '../services/walletService';

export function useTeacherRevenueSummary() {
  return useQuery({
    queryKey: ['teacher-revenue-summary'],
    queryFn: async () => {
      const response = await walletService.getTeacherRevenueSummary();
      return response.data;
    },
  });
}
