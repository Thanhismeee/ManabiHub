package com.manabihub.challenge.service;

import com.manabihub.challenge.dto.WeeklyChallengeLeaderboardResponse;
import com.manabihub.challenge.entity.WeeklyLearningChallenge;
import com.manabihub.challenge.enums.ChallengeStatus;
import com.manabihub.challenge.repository.WeeklyLearningChallengeAttemptRepository;
import com.manabihub.challenge.repository.WeeklyLearningChallengeRepository;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyChallengeLeaderboardServiceTest {
    @Mock private WeeklyLearningChallengeRepository challengeRepository;
    @Mock private WeeklyLearningChallengeAttemptRepository attemptRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private CourseRepository courseRepository;
    @InjectMocks private WeeklyChallengeLeaderboardService service;

    @Test
    void forStudent_returnsServerRankingRewardAndCurrentStudentMarker() {
        UUID userId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID firstStudentId = UUID.randomUUID();
        UUID currentStudentId = UUID.randomUUID();
        WeeklyLearningChallenge challenge = challenge(challengeId, ChallengeStatus.PUBLISHED);
        StudentProfile currentStudent = profile(currentStudentId, "Học viên B", "b.png");
        WeeklyLearningChallengeAttemptRepository.BestScore firstScore = score(firstStudentId, 10000L);
        WeeklyLearningChallengeAttemptRepository.BestScore currentScore = score(currentStudentId, 12500L);

        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(currentStudent));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(attemptRepository.findRankedBestScores(challengeId)).thenReturn(List.of(firstScore, currentScore));
        when(studentProfileRepository.findAllWithUserByIdIn(List.of(firstStudentId, currentStudentId)))
                .thenReturn(List.of(profile(firstStudentId, "Học viên A", "a.png"), currentStudent));

        WeeklyChallengeLeaderboardResponse response = service.forStudent(userId, challengeId);

        assertEquals(2, response.totalParticipants());
        assertEquals("Học viên A", response.entries().getFirst().displayName());
        assertEquals(BigDecimal.valueOf(30000), response.entries().getFirst().rewardAmount());
        assertNotNull(response.currentStudent());
        assertEquals(2, response.currentStudent().rank());
        assertTrue(response.currentStudent().currentStudent());
        assertEquals(BigDecimal.valueOf(20000), response.currentStudent().rewardAmount());
    }

    @Test
    void forCourseManager_usesSameRankingOrder() {
        UUID adminId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        WeeklyLearningChallengeAttemptRepository.BestScore score = score(studentId, 9000L);
        when(courseRepository.hasAdminRole(adminId, List.of("COURSE_MANAGER"))).thenReturn(true);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge(challengeId, ChallengeStatus.ARCHIVED)));
        when(attemptRepository.findRankedBestScores(challengeId)).thenReturn(List.of(score));
        when(studentProfileRepository.findAllWithUserByIdIn(List.of(studentId)))
                .thenReturn(List.of(profile(studentId, "Nguyễn An", null)));

        WeeklyChallengeLeaderboardResponse response = service.forCourseManager(adminId, challengeId);

        assertEquals(1, response.entries().size());
        assertEquals(1, response.entries().getFirst().rank());
        assertFalse(response.entries().getFirst().currentStudent());
        assertNull(response.currentStudent());
    }

    private WeeklyLearningChallenge challenge(UUID id, ChallengeStatus status) {
        return WeeklyLearningChallenge.builder().id(id).title("Manabi Match · N5")
                .weekStart(LocalDate.of(2026, 8, 10)).status(status)
                .firstPrize(BigDecimal.valueOf(30000)).secondPrize(BigDecimal.valueOf(20000))
                .thirdPrize(BigDecimal.valueOf(10000)).build();
    }

    private StudentProfile profile(UUID id, String name, String avatarUrl) {
        return StudentProfile.builder().id(id).displayName(name)
                .user(AppUser.builder().id(UUID.randomUUID()).email(name + "@example.com")
                        .fullName(name).avatarUrl(avatarUrl).build()).build();
    }

    private WeeklyLearningChallengeAttemptRepository.BestScore score(UUID studentId, long bestMillis) {
        WeeklyLearningChallengeAttemptRepository.BestScore score = mock(WeeklyLearningChallengeAttemptRepository.BestScore.class);
        when(score.getStudentId()).thenReturn(studentId);
        when(score.getBestMillis()).thenReturn(bestMillis);
        return score;
    }
}
