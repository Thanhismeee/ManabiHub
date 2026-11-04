import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import { isAxiosError } from 'axios';
import { getAuthSession } from '../../../shared/auth/authSession';
import { normalizeVideoProgressPayload } from '../utils/videoProgress';
import type {
  CourseLearning,
  CourseProgressSummary,
  FinalTestAttempt,
  FinalTestEligibility,
  FinalTestSubmissionResult,
  LessonProgress,
  LearningCertificate,
  QuizSubmissionResult,
  WritingSubmissionDetail,
} from '../types';

export const learningService = {
  openCourse: async (courseId: string): Promise<CourseLearning> => {
    const response = await axiosClient.get(ENDPOINTS.LEARNING.COURSE_LEARN(courseId));
    return response.data.data;
  },

  getCourseProgress: async (courseId: string): Promise<CourseProgressSummary> => {
    const response = await axiosClient.get(ENDPOINTS.LEARNING.COURSE_PROGRESS(courseId));
    return response.data.data;
  },

  getCertificate: async (courseId: string): Promise<LearningCertificate | null> => {
    try {
      const response = await axiosClient.get(ENDPOINTS.LEARNING.CERTIFICATE(courseId));
      return response.data.data;
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  },

  generateCertificate: async (courseId: string): Promise<LearningCertificate> => {
    const response = await axiosClient.post(ENDPOINTS.LEARNING.CERTIFICATE(courseId));
    return response.data.data;
  },

  saveVideoProgress: async (
    blockId: string,
    positionSeconds: number,
    watchedSeconds = 0,
    mediaDurationSeconds?: number,
  ): Promise<LessonProgress> => {
    const progress = normalizeVideoProgressPayload(
      positionSeconds,
      watchedSeconds,
      mediaDurationSeconds,
    );
    const response = await axiosClient.put(ENDPOINTS.LEARNING.VIDEO_PROGRESS(blockId), {
      ...progress,
    });
    return response.data.data;
  },

  saveVideoProgressKeepalive: (
    blockId: string,
    positionSeconds: number,
    watchedSeconds: number,
    mediaDurationSeconds?: number,
  ): void => {
    if (typeof window === 'undefined' || typeof window.fetch !== 'function') return;

    const session = getAuthSession('public');
    const endpoint = axiosClient.getUri({ url: ENDPOINTS.LEARNING.VIDEO_PROGRESS(blockId) });
    const url = new URL(endpoint, window.location.origin).toString();
    const body = JSON.stringify(normalizeVideoProgressPayload(
      positionSeconds,
      watchedSeconds,
      mediaDurationSeconds,
    ));

    void window.fetch(url, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        ...(session ? { Authorization: `Bearer ${session.token}` } : {}),
      },
      body,
      credentials: 'include',
      keepalive: true,
    }).catch(() => undefined);
  },

  reviewFlashcard: async (blockId: string, cardIndex: number, status: 'REMEMBERED' | 'NEEDS_REVIEW'): Promise<LessonProgress> => {
    const response = await axiosClient.put(ENDPOINTS.LEARNING.FLASHCARD_REVIEW(blockId), {
      cardIndex,
      status,
    });
    return response.data.data;
  },

  submitQuiz: async (blockId: string, answers: string[]): Promise<QuizSubmissionResult> => {
    const response = await axiosClient.post(ENDPOINTS.LEARNING.QUIZ_SUBMIT(blockId), { answers });
    return response.data.data;
  },

  getFinalTestEligibility: async (courseId: string): Promise<FinalTestEligibility> => {
    const response = await axiosClient.get(ENDPOINTS.LEARNING.FINAL_TEST_ELIGIBILITY(courseId));
    return response.data.data;
  },

  startFinalTest: async (courseId: string): Promise<FinalTestAttempt> => {
    const response = await axiosClient.post(ENDPOINTS.LEARNING.FINAL_TEST_START(courseId));
    return response.data.data;
  },

  terminateFinalTest: async (courseId: string, attemptId: string): Promise<void> => {
    await axiosClient.post(ENDPOINTS.LEARNING.FINAL_TEST_TERMINATE(courseId, attemptId));
  },

  submitFinalTest: async (
    courseId: string,
    attemptId: string,
    answers: Array<{ questionId: string; selectedChoiceIds: string[] }>,
  ): Promise<FinalTestSubmissionResult> => {
    const response = await axiosClient.post(ENDPOINTS.LEARNING.FINAL_TEST_SUBMIT(courseId), {
      attemptId,
      answers,
    });
    return response.data.data;
  },

  markLessonComplete: async (blockId: string): Promise<LessonProgress> => {
    const response = await axiosClient.post(ENDPOINTS.LEARNING.MARK_COMPLETE(blockId));
    return response.data.data;
  },

  getWritingSubmission: async (blockId: string): Promise<WritingSubmissionDetail | null> => {
    const response = await axiosClient.get(ENDPOINTS.LEARNING.WRITING_SUBMISSION_GET(blockId));
    return response.data.data;
  },

  saveWritingDraft: async (blockId: string, content: string): Promise<WritingSubmissionDetail> => {
    const response = await axiosClient.put(ENDPOINTS.LEARNING.WRITING_SUBMISSION_DRAFT(blockId), { content });
    return response.data.data;
  },

  submitWriting: async (blockId: string, content: string): Promise<WritingSubmissionDetail> => {
    const response = await axiosClient.post(ENDPOINTS.LEARNING.WRITING_SUBMISSION_POST(blockId), { content });
    return response.data.data;
  },

  requestAiWritingAssistance: async (blockId: string, submissionId: string): Promise<WritingSubmissionDetail> => {
    const response = await axiosClient.post(ENDPOINTS.LEARNING.WRITING_SUBMISSION_AI(blockId, submissionId));
    return response.data.data;
  },
};
