import { axiosClient } from '../../../shared/api/axiosClient';
import type { LearningLanguage, ProficiencyFramework } from '../../../shared/learning/languageProfiles';

export interface FinalTestChoice {
  id?: string;
  content: string;
  isCorrect: boolean;
}

export interface FinalTestQuestion {
  id?: string;
  content: string;
  explanation: string;
  choices: FinalTestChoice[];
}

export interface FinalTestConfig {
  id?: string;
  courseId: string;
  timeLimitMinutes: number;
  passingScore: number;
  maxRetakes: number;
  jlptLevel?: string | null;
  learningLanguage: LearningLanguage;
  proficiencyFramework: ProficiencyFramework;
  frameworkVersion: string;
  proficiencyLevel: string;
  assessmentFormat: string;
  skillFocus: string;
  questions: FinalTestQuestion[];
}

export type UpdateFinalTestRequest = Omit<FinalTestConfig, 'id' | 'courseId'>;

export const finalTestService = {
  getFinalTest: async (courseId: string): Promise<FinalTestConfig | null> => {
    try {
      const { data } = await axiosClient.get<{ data: FinalTestConfig }>(`/v1/teacher/courses/${courseId}/final-test`);
      return data.data;
    } catch (err: any) {
      // Return null only if not configured yet (404 Not Found)
      if (err.response?.status === 404) {
        return null;
      }
      // Re-throw other errors (e.g. 500, network error) so UI can show a failure state
      throw err;
    }
  },

  updateFinalTest: async (courseId: string, request: UpdateFinalTestRequest): Promise<FinalTestConfig> => {
    const { data } = await axiosClient.put<{ data: FinalTestConfig }>(`/v1/teacher/courses/${courseId}/final-test`, request);
    return data.data;
  },
};
