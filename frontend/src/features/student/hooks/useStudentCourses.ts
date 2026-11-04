import { useQuery } from '@tanstack/react-query';
import { studentService } from '../services/studentService';
import type { PageResponse } from '../../../shared/types/api';
import type { StudentCourseSummary } from '../types/studentTypes';

export const useStudentCourses = (page: number, size: number) => {
  return useQuery<PageResponse<StudentCourseSummary>>({
    queryKey: ['student-courses', page, size],
    queryFn: () => studentService.getEnrolledCourses({ page, size }),
  });
};
