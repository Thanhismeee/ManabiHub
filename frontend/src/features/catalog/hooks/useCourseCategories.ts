import { useQuery } from '@tanstack/react-query';
import { catalogService } from '../services/catalogService';

export const useCourseCategories = () => {
  return useQuery({
    queryKey: ['course-categories'],
    queryFn: () => catalogService.getCategories(),
    staleTime: Infinity, // Categories rarely change
  });
};
