import { useQuery } from '@tanstack/react-query';
import { aiChatService } from '../services/aiChatService';

export function useAiChatEligibility(courseId?: string, lessonBlockId?: string) {
  return useQuery({
    queryKey: ['ai-chat', 'eligibility', courseId, lessonBlockId],
    queryFn: () => aiChatService.getEligibility(courseId!, lessonBlockId!),
    enabled: Boolean(courseId && lessonBlockId),
    staleTime: 30_000,
  });
}
