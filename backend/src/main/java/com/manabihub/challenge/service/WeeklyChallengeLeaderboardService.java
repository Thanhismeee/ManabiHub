package com.manabihub.challenge.service;

import com.manabihub.challenge.dto.WeeklyChallengeLeaderboardResponse;
import com.manabihub.challenge.dto.WeeklyChallengeLeaderboardResponse.LeaderboardEntry;
import com.manabihub.challenge.entity.WeeklyLearningChallenge;
import com.manabihub.challenge.enums.ChallengeStatus;
import com.manabihub.challenge.repository.WeeklyLearningChallengeAttemptRepository;
import com.manabihub.challenge.repository.WeeklyLearningChallengeRepository;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WeeklyChallengeLeaderboardService {
    private static final int PUBLIC_RANK_LIMIT = 20;
    private static final String PRIVATE_STUDENT_NAME = "Học viên ManabiHub";

    private final WeeklyLearningChallengeRepository challengeRepository;
    private final WeeklyLearningChallengeAttemptRepository attemptRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public WeeklyChallengeLeaderboardResponse forStudent(UUID userId, UUID challengeId) {
        StudentProfile student = studentProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(MessageCodes.LEARNING_STUDENT_PROFILE_NOT_FOUND,
                        "Không tìm thấy hồ sơ học viên", HttpStatus.NOT_FOUND));
        WeeklyLearningChallenge challenge = requireChallenge(challengeId);
        if (challenge.getStatus() == ChallengeStatus.DRAFT) {
            throw new BusinessException(MessageCodes.WEEKLY_CHALLENGE_NOT_AVAILABLE,
                    "Bảng xếp hạng chưa được công khai", HttpStatus.NOT_FOUND);
        }
        return build(challenge, student.getId());
    }

    @Transactional(readOnly = true)
    public WeeklyChallengeLeaderboardResponse forCourseManager(UUID adminId, UUID challengeId) {
        if (!courseRepository.hasAdminRole(adminId, List.of("COURSE_MANAGER"))) {
            throw new BusinessException(MessageCodes.WEEKLY_CHALLENGE_COURSE_MANAGER_REQUIRED,
                    "Chỉ Course Manager được xem bảng xếp hạng quản trị", HttpStatus.FORBIDDEN);
        }
        return build(requireChallenge(challengeId), null);
    }

    private WeeklyChallengeLeaderboardResponse build(WeeklyLearningChallenge challenge, UUID currentStudentId) {
        List<WeeklyLearningChallengeAttemptRepository.BestScore> scores =
                attemptRepository.findRankedBestScores(challenge.getId());
        List<UUID> studentIds = scores.stream()
                .map(WeeklyLearningChallengeAttemptRepository.BestScore::getStudentId)
                .toList();
        Map<UUID, StudentProfile> profiles = studentIds.isEmpty()
                ? Map.of()
                : studentProfileRepository.findAllWithUserByIdIn(studentIds).stream()
                        .collect(Collectors.toMap(StudentProfile::getId, Function.identity(),
                                (first, ignored) -> first, LinkedHashMap::new));

        List<LeaderboardEntry> topEntries = new ArrayList<>();
        LeaderboardEntry currentEntry = null;
        for (int index = 0; index < scores.size(); index++) {
            WeeklyLearningChallengeAttemptRepository.BestScore score = scores.get(index);
            boolean current = score.getStudentId().equals(currentStudentId);
            LeaderboardEntry entry = new LeaderboardEntry(index + 1,
                    displayName(profiles.get(score.getStudentId())),
                    avatarUrl(profiles.get(score.getStudentId())),
                    score.getBestMillis(), rewardForRank(challenge, index + 1), current);
            if (index < PUBLIC_RANK_LIMIT) topEntries.add(entry);
            if (current) currentEntry = entry;
        }

        return new WeeklyChallengeLeaderboardResponse(challenge.getId(), challenge.getTitle(),
                challenge.getWeekStart(), challenge.getWeekStart().plusDays(6),
                challenge.getSettledAt() != null, Instant.now(), scores.size(),
                List.copyOf(topEntries), currentEntry);
    }

    private WeeklyLearningChallenge requireChallenge(UUID challengeId) {
        return challengeRepository.findById(challengeId)
                .orElseThrow(() -> new BusinessException(MessageCodes.WEEKLY_CHALLENGE_NOT_FOUND,
                        "Không tìm thấy thử thách", HttpStatus.NOT_FOUND));
    }

    private String displayName(StudentProfile profile) {
        if (profile == null || profile.getDisplayName() == null || profile.getDisplayName().isBlank()) {
            return PRIVATE_STUDENT_NAME;
        }
        return profile.getDisplayName().trim();
    }

    private String avatarUrl(StudentProfile profile) {
        return profile == null || profile.getUser() == null ? null : profile.getUser().getAvatarUrl();
    }

    private BigDecimal rewardForRank(WeeklyLearningChallenge challenge, int rank) {
        return switch (rank) {
            case 1 -> challenge.getFirstPrize();
            case 2 -> challenge.getSecondPrize();
            case 3 -> challenge.getThirdPrize();
            default -> BigDecimal.ZERO;
        };
    }
}
