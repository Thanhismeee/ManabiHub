package com.manabihub.challenge.service;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.challenge.dto.*;
import com.manabihub.challenge.entity.*;
import com.manabihub.challenge.enums.ChallengeStatus;
import com.manabihub.challenge.repository.*;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.exception.ValidationBusinessException;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.dto.response.ValidationError;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WeeklyChallengeManagementService {
    private static final String ERROR = MessageCodes.WEEKLY_CHALLENGE_INVALID;
    private static final String CONFLICT_ERROR = MessageCodes.WEEKLY_CHALLENGE_CONFLICT;
    private final WeeklyLearningChallengeRepository challengeRepository;
    private final WeeklyLearningChallengePairRepository pairRepository;
    private final WeeklyLearningChallengeAttemptRepository attemptRepository;
    private final WeeklyLearningChallengeRewardRepository rewardRepository;
    private final DailyLearningAttendanceRewardRepository attendanceRewardRepository;
    private final CourseRepository courseRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public List<WeeklyChallengeResponse> list(UUID adminId) {
        requireCourseManager(adminId);
        return challengeRepository.findAllByOrderByWeekStartDesc().stream().map(this::map).toList();
    }

    @Transactional
    public WeeklyChallengeResponse create(UUID adminId, UpsertWeeklyChallengeRequest request) {
        requireCourseManager(adminId);
        validate(request);
        if (challengeRepository.findByWeekStart(request.weekStart()).isPresent()) {
            throw conflict("Tuần này đã có một thử thách");
        }
        WeeklyLearningChallenge challenge = WeeklyLearningChallenge.builder()
                .id(UUID.randomUUID()).status(ChallengeStatus.DRAFT).createdBy(adminId).build();
        apply(challenge, request);
        try {
            // Kiem tra findByWeekStart o tren la doc-roi-ghi khong khoa: hai yeu cau
            // dong thoi deu co the vuot qua no. uq_weekly_learning_challenges_week moi
            // la bao dam that, nen phai dich ngoai le cua no thanh 409 thay vi de roi
            // xuong handler chung va tra 500.
            challengeRepository.saveAndFlush(challenge);
        } catch (DataIntegrityViolationException ex) {
            throw conflict("Tuần này đã có một thử thách");
        }
        replacePairs(challenge.getId(), request.pairs());
        audit(adminId, "WEEKLY_CHALLENGE_CREATED", challenge.getId(), Map.of("weekStart", request.weekStart().toString()));
        return map(challenge);
    }

    @Transactional
    public WeeklyChallengeResponse update(UUID adminId, UUID id, UpsertWeeklyChallengeRequest request) {
        requireCourseManager(adminId);
        validate(request);
        WeeklyLearningChallenge challenge = requireLocked(id);
        if (challenge.getStatus() != ChallengeStatus.DRAFT || hasRecordedActivity(id)) {
            throw conflict("Chỉ được sửa bản nháp chưa phát sinh lượt chơi hoặc tiền thưởng");
        }
        challengeRepository.findByWeekStart(request.weekStart())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw conflict("Tuần này đã có một thử thách"); });
        apply(challenge, request);
        replacePairs(id, request.pairs());
        audit(adminId, "WEEKLY_CHALLENGE_UPDATED", id, Map.of("weekStart", request.weekStart().toString()));
        return map(challenge);
    }

    @Transactional
    public WeeklyChallengeResponse publish(UUID adminId, UUID id) {
        requireCourseManager(adminId);
        WeeklyLearningChallenge challenge = requireLocked(id);
        if (challenge.getStatus() == ChallengeStatus.PUBLISHED) return map(challenge);
        if (challenge.getStatus() != ChallengeStatus.DRAFT || pairRepository.countByChallengeId(id) < 4) {
            throw conflict("Chỉ bản nháp có ít nhất 4 cặp nội dung mới được công khai");
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        if (challenge.getWeekStart().plusDays(6).isBefore(today)) {
            throw conflict("Không thể công khai thử thách của một tuần đã kết thúc");
        }
        challenge.setStatus(ChallengeStatus.PUBLISHED);
        challenge.setPublishedBy(adminId);
        challenge.setPublishedAt(Instant.now());
        audit(adminId, "WEEKLY_CHALLENGE_PUBLISHED", id, Map.of("weekStart", challenge.getWeekStart().toString()));
        return map(challenge);
    }

    @Transactional
    public WeeklyChallengeResponse unpublish(UUID adminId, UUID id) {
        requireCourseManager(adminId);
        WeeklyLearningChallenge challenge = requireLocked(id);
        if (challenge.getStatus() != ChallengeStatus.PUBLISHED) {
            throw conflict("Chỉ thử thách đang công khai mới có thể chuyển về bản nháp");
        }
        if (hasRecordedActivity(id)) {
            throw conflict("Không thể ẩn thử thách đã phát sinh lượt chơi hoặc tiền thưởng; hãy để hệ thống chốt tuần");
        }
        challenge.setStatus(ChallengeStatus.DRAFT);
        challenge.setPublishedBy(null);
        challenge.setPublishedAt(null);
        audit(adminId, "WEEKLY_CHALLENGE_UNPUBLISHED", id, Map.of());
        return map(challenge);
    }

    @Transactional
    public void delete(UUID adminId, UUID id) {
        requireCourseManager(adminId);
        WeeklyLearningChallenge challenge = requireLocked(id);
        if (challenge.getStatus() != ChallengeStatus.DRAFT || hasRecordedActivity(id)) {
            throw conflict("Không thể xóa thử thách đã công khai hoặc đã phát sinh lượt chơi, tiền thưởng");
        }
        challengeRepository.delete(challenge);
        audit(adminId, "WEEKLY_CHALLENGE_DELETED", id, Map.of());
    }

    private void validate(UpsertWeeklyChallengeRequest request) {
        if (request.weekStart().getDayOfWeek() != DayOfWeek.MONDAY) {
            throw invalid("Ngày bắt đầu tuần phải là Thứ Hai");
        }
        if (request.firstPrize().compareTo(request.secondPrize()) < 0
                || request.secondPrize().compareTo(request.thirdPrize()) < 0) {
            throw validation("Mức thưởng phải giảm dần theo hạng 1, 2, 3");
        }
        Set<String> prompts = new HashSet<>();
        for (ChallengePairRequest pair : request.pairs()) {
            if (!prompts.add(pair.prompt().trim().toLowerCase(Locale.ROOT))) {
                throw invalid("Nội dung thẻ không được trùng trong cùng thử thách");
            }
        }
    }

    private void apply(WeeklyLearningChallenge c, UpsertWeeklyChallengeRequest r) {
        c.setWeekStart(r.weekStart());
        c.setTitle(r.title().trim());
        c.setDescription(r.description().trim());
        c.setJlptLevel(r.jlptLevel());
        c.setDailyRankedLimit(r.dailyRankedLimit());
        c.setWrongPenaltySeconds(r.wrongPenaltySeconds());
        c.setDailyAttendanceReward(r.dailyAttendanceReward());
        c.setFirstPrize(r.firstPrize());
        c.setSecondPrize(r.secondPrize());
        c.setThirdPrize(r.thirdPrize());
    }

    private void replacePairs(UUID challengeId, List<ChallengePairRequest> pairs) {
        pairRepository.deleteByChallengeId(challengeId);
        for (int i = 0; i < pairs.size(); i++) {
            ChallengePairRequest pair = pairs.get(i);
            pairRepository.save(WeeklyLearningChallengePair.builder()
                    .id(UUID.randomUUID()).challengeId(challengeId)
                    .prompt(pair.prompt().trim()).answer(pair.answer().trim()).orderIndex(i).build());
        }
    }

    private WeeklyChallengeResponse map(WeeklyLearningChallenge c) {
        List<WeeklyChallengeResponse.ChallengePairResponse> pairs = pairRepository
                .findByChallengeIdOrderByOrderIndex(c.getId()).stream()
                .map(pair -> new WeeklyChallengeResponse.ChallengePairResponse(
                        pair.getId(), pair.getPrompt(), pair.getAnswer(), pair.getOrderIndex()))
                .toList();
        return new WeeklyChallengeResponse(c.getId(), c.getWeekStart(), c.getWeekStart().plusDays(6),
                c.getTitle(), c.getDescription(), c.getJlptLevel(), c.getStatus(), c.getDailyRankedLimit(),
                c.getWrongPenaltySeconds(), c.getDailyAttendanceReward(), c.getFirstPrize(), c.getSecondPrize(),
                c.getThirdPrize(), pairs, c.getPublishedAt(), c.getSettledAt(), null, 0);
    }

    private WeeklyLearningChallenge requireLocked(UUID id) {
        return challengeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(MessageCodes.WEEKLY_CHALLENGE_NOT_FOUND, "Không tìm thấy thử thách", HttpStatus.NOT_FOUND));
    }

    private boolean hasRecordedActivity(UUID challengeId) {
        return attemptRepository.existsByChallengeId(challengeId)
                || rewardRepository.existsByChallengeId(challengeId)
                || attendanceRewardRepository.existsByChallengeId(challengeId);
    }

    private void requireCourseManager(UUID adminId) {
        if (!courseRepository.hasAdminRole(adminId, List.of("COURSE_MANAGER"))) {
            throw new BusinessException(MessageCodes.WEEKLY_CHALLENGE_COURSE_MANAGER_REQUIRED, "Chỉ Course Manager được quản lý trò chơi", HttpStatus.FORBIDDEN);
        }
    }

    private void audit(UUID adminId, String action, UUID targetId, Map<String, Object> metadata) {
        auditLogRepository.save(AuditLog.builder().actorType("INTERNAL_ADMIN").actorAdminId(adminId)
                .actorRoleCode("COURSE_MANAGER").action(action).targetType("WEEKLY_CHALLENGE")
                .targetId(targetId).metadata(metadata).build());
    }

    private BusinessException invalid(String message) { return new BusinessException(ERROR, message, HttpStatus.BAD_REQUEST); }
    private ValidationBusinessException validation(String message) {
        return new ValidationBusinessException(
                MessageCodes.VALIDATION_FAILED,
                message,
                List.of(new ValidationError("secondPrize", message, "error"))
        );
    }
    private BusinessException conflict(String message) { return new BusinessException(CONFLICT_ERROR, message, HttpStatus.CONFLICT); }
}
