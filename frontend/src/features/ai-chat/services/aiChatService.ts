import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { ApiResponse } from '../../../shared/types/api';
import type { AiChatEligibility, AiChatMessage, AiChatMessageRequest } from '../types';

export const aiChatService = {
  getEligibility: async (courseId: string, lessonBlockId: string): Promise<AiChatEligibility> => {
    const response = await axiosClient.get<ApiResponse<AiChatEligibility>>(
      ENDPOINTS.studentAiChat.eligibility(courseId, lessonBlockId),
    );
    return response.data.data;
  },

  sendMessage: async (
    courseId: string,
    lessonBlockId: string,
    request: AiChatMessageRequest,
  ): Promise<AiChatMessage> => {
    const response = await axiosClient.post<ApiResponse<AiChatMessage>>(
      ENDPOINTS.studentAiChat.messages(courseId, lessonBlockId),
      request,
    );
    return response.data.data;
  },
};
