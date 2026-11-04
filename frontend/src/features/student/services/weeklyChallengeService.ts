import { axiosClient } from '../../../shared/api/axiosClient';
import { ENDPOINTS } from '../../../shared/api/endpoints';
import type { WeeklyChallengeLeaderboard } from '../../../shared/types/weeklyChallengeLeaderboard';

export interface WeeklyChallenge {
  id: string;
  weekStart: string;
  weekEnd: string;
  title: string;
  description: string;
  jlptLevel: string;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  dailyRankedLimit: number;
  wrongPenaltySeconds: number;
  dailyAttendanceReward: number;
  firstPrize: number;
  secondPrize: number;
  thirdPrize: number;
  personalBestMillis: number | null;
  rankedAttemptsToday: number;
}

export interface ChallengeAttempt {
  attemptId: string;
  ranked: boolean;
  remainingRankedAttempts: number;
  cards: Array<{ id: string; value: string; position: number; matched: boolean }>;
  matchedPairs: number;
  totalPairs: number;
  penaltyMillis: number;
  totalMillis: number | null;
  expiresAt: string;
  completed: boolean;
}

export const weeklyChallengeService = {
  async current(): Promise<WeeklyChallenge | null> {
    try {
      const response = await axiosClient.get(ENDPOINTS.student.weeklyChallenge);
      return response.data.data;
    } catch (error: unknown) {
      const status = (error as { response?: { status?: number } }).response?.status;
      if (status === 404) return null;
      throw error;
    }
  },
  async start(challengeId: string): Promise<ChallengeAttempt> {
    const response = await axiosClient.post(ENDPOINTS.student.startWeeklyChallenge(challengeId));
    return response.data.data;
  },
  async match(attemptId: string, firstCardId: string, secondCardId: string): Promise<ChallengeAttempt> {
    const response = await axiosClient.post(ENDPOINTS.student.matchWeeklyChallenge(attemptId), {
      firstCardId,
      secondCardId,
    });
    return response.data.data;
  },
  async leaderboard(challengeId: string): Promise<WeeklyChallengeLeaderboard> {
    const response = await axiosClient.get(ENDPOINTS.student.weeklyChallengeLeaderboard(challengeId));
    return response.data.data;
  },
};
