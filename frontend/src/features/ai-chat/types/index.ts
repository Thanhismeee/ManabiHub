export interface AiChatEligibility {
  eligible: boolean;
  unavailableCode: string | null;
  message: string;
}

export interface AiChatMessageRequest {
  question: string;
  history?: { role: 'user' | 'assistant'; content: string }[];
}

export interface AiChatMessage {
  courseId: string;
  lessonBlockId: string;
  answer: string;
  disclaimerCode: string;
  provider: string;
}

export interface SendAiChatMessageInput extends AiChatMessageRequest {
  courseId: string;
  lessonBlockId: string;
}
