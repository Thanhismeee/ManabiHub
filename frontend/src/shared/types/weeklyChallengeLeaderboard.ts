export interface WeeklyChallengeLeaderboardEntry {
  rank: number;
  displayName: string;
  avatarUrl: string | null;
  bestMillis: number;
  rewardAmount: number;
  currentStudent: boolean;
}

export interface WeeklyChallengeLeaderboard {
  challengeId: string;
  challengeTitle: string;
  weekStart: string;
  weekEnd: string;
  settled: boolean;
  generatedAt: string;
  totalParticipants: number;
  entries: WeeklyChallengeLeaderboardEntry[];
  currentStudent: WeeklyChallengeLeaderboardEntry | null;
}
