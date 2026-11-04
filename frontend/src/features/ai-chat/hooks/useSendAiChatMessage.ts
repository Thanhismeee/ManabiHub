import { useMutation } from '@tanstack/react-query';
import { aiChatService } from '../services/aiChatService';
import type { SendAiChatMessageInput } from '../types';

export function useSendAiChatMessage() {
  return useMutation({
    mutationFn: ({ courseId, lessonBlockId, question }: SendAiChatMessageInput) =>
      aiChatService.sendMessage(courseId, lessonBlockId, { question }),
  });
}
