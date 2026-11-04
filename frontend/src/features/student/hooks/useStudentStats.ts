import { useQuery } from '@tanstack/react-query';
import { studentService } from '../services/studentService';
import type { StudentDashboardStats } from '../types/studentTypes';

export const useStudentStats = () => {
  return useQuery<StudentDashboardStats>({
    queryKey: ['student-stats'],
    queryFn: () => studentService.getDashboardStats(),
  });
};
