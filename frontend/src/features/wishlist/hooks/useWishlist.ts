import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getAuthSession } from '../../../shared/auth/authSession';
import { ROLES } from '../../../shared/constants/roles';
import { wishlistService } from '../services/wishlistService';
import type { WishlistItem } from '../types';

export const wishlistKey = ['student-wishlist'] as const;

export function useWishlist() {
  const queryClient = useQueryClient();
  const session = getAuthSession('public');
  const isStudent = session?.roles.includes(ROLES.STUDENT) ?? false;
  const query = useQuery({
    queryKey: wishlistKey,
    queryFn: wishlistService.list,
    enabled: isStudent,
    staleTime: 30_000,
  });

  const addMutation = useMutation({
    mutationFn: wishlistService.add,
    onSuccess: (item) => {
      queryClient.setQueryData<WishlistItem[]>(wishlistKey, (current = []) => {
        if (current.some((entry) => entry.courseId === item.courseId)) {
          return current;
        }
        return [item, ...current];
      });
    },
  });

  const removeMutation = useMutation({
    mutationFn: wishlistService.remove,
    onSuccess: (_data, courseId) => {
      queryClient.setQueryData<WishlistItem[]>(wishlistKey, (current = []) =>
        current.filter((entry) => entry.courseId !== courseId),
      );
    },
  });

  return {
    ...query,
    isStudent,
    courseIds: new Set((query.data ?? []).map((item) => item.courseId)),
    addCourse: addMutation.mutateAsync,
    removeCourse: removeMutation.mutateAsync,
    isUpdating: addMutation.isPending || removeMutation.isPending,
  };
}
