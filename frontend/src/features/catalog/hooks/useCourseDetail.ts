import { useQuery } from '@tanstack/react-query';
import { catalogService } from '../services/catalogService';

export const useCourseDetail = (id: string) => {
  return useQuery({
    queryKey: ['public-course', id],
    queryFn: () => catalogService.getCourseDetail(id),
    enabled: !!id,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });
};
