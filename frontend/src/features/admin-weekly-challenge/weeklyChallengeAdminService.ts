import { axiosClient } from '../../shared/api/axiosClient';
import { ENDPOINTS } from '../../shared/api/endpoints';
import type { WeeklyChallengeLeaderboard } from '../../shared/types/weeklyChallengeLeaderboard';

export interface ChallengePair { id?: string; prompt: string; answer: string; orderIndex?: number }
export interface ManagedWeeklyChallenge {
  id: string; weekStart: string; weekEnd: string; title: string; description: string;
  jlptLevel: string; status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'; dailyRankedLimit: number;
  wrongPenaltySeconds: number; dailyAttendanceReward: number; firstPrize: number;
  secondPrize: number; thirdPrize: number; pairs: ChallengePair[]; settledAt?: string | null;
}
export type WeeklyChallengePayload = Omit<ManagedWeeklyChallenge, 'id' | 'weekEnd' | 'status' | 'settledAt'>;

export const weeklyChallengeAdminService = {
  async list(): Promise<ManagedWeeklyChallenge[]> {
    const response = await axiosClient.get(ENDPOINTS.ADMIN_WEEKLY_CHALLENGES.LIST);
    return response.data.data;
  },
  async create(payload: WeeklyChallengePayload): Promise<ManagedWeeklyChallenge> {
    const response = await axiosClient.post(ENDPOINTS.ADMIN_WEEKLY_CHALLENGES.LIST, payload);
    return response.data.data;
  },
  async update(id: string, payload: WeeklyChallengePayload): Promise<ManagedWeeklyChallenge> {
    const response = await axiosClient.put(ENDPOINTS.ADMIN_WEEKLY_CHALLENGES.DETAIL(id), payload);
    return response.data.data;
  },
  async publish(id: string): Promise<void> { await axiosClient.post(ENDPOINTS.ADMIN_WEEKLY_CHALLENGES.PUBLISH(id)); },
  async unpublish(id: string): Promise<void> { await axiosClient.post(ENDPOINTS.ADMIN_WEEKLY_CHALLENGES.UNPUBLISH(id)); },
  async remove(id: string): Promise<void> { await axiosClient.delete(ENDPOINTS.ADMIN_WEEKLY_CHALLENGES.DETAIL(id)); },
  async leaderboard(id: string): Promise<WeeklyChallengeLeaderboard> {
    const response = await axiosClient.get(ENDPOINTS.ADMIN_WEEKLY_CHALLENGES.LEADERBOARD(id));
    return response.data.data;
  },
};
