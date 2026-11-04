package com.manabihub.cohort.service;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.cohort.domain.CohortMember;
import com.manabihub.cohort.domain.CohortMemberStatus;
import com.manabihub.cohort.domain.CohortStatus;
import com.manabihub.cohort.domain.LearningCohort;
import com.manabihub.cohort.dto.AddCohortMemberRequest;
import com.manabihub.cohort.dto.CohortResponse;
import com.manabihub.cohort.dto.CreateCohortRequest;
import com.manabihub.cohort.repository.CohortMemberRepository;
import com.manabihub.cohort.repository.LearningCohortRepository;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.enums.EnrollmentAccessSource;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LearningCohortService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LearningCohortRepository cohortRepository;
    private final CohortMemberRepository memberRepository;
    private final CourseRepository courseRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final com.manabihub.learning.service.EnrollmentAccessService enrollmentAccessService;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<CohortResponse> listMine() {
        UUID userId = currentUserService.getCurrentUserId();
        return cohortRepository.findByOwnerTeacher_User_IdOrderByStartDateDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CohortResponse getMine(UUID cohortId) {
        return toResponse(resolveOwnedCohort(cohortId));
    }

    public CohortResponse create(CreateCohortRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        TeacherProfile teacher = teacherProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.MSG_KYC_010,
                        "Không tìm thấy hồ sơ giáo viên.",
                        HttpStatus.FORBIDDEN));
        Course course = courseRepository.findByIdAndTeacher_Id(request.courseId(), teacher.getId())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COURSE_NOT_FOUND,
                        "Không tìm thấy khóa học của giáo viên.",
                        HttpStatus.NOT_FOUND));
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessException(
                    MessageCodes.COMMON_BAD_REQUEST,
                    "Chỉ có thể mở lớp từ khóa học đã xuất bản.",
                    HttpStatus.CONFLICT);
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED,
                    "Ngày kết thúc lớp phải bằng hoặc sau ngày bắt đầu.");
        }

        String code = normalizeCode(request.code(), request.name());
        if (cohortRepository.existsByCode(code)) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST,
                    "Mã lớp đã tồn tại.", HttpStatus.CONFLICT);
        }
        LearningCohort cohort = LearningCohort.builder()
                .course(course)
                .ownerTeacher(teacher)
                .name(request.name().trim())
                .code(code)
                .organizationName(StringUtils.hasText(request.organizationName())
                        ? request.organizationName().trim()
                        : "SOFL")
                .branchName(trimToNull(request.branchName()))
                .startDate(request.startDate())
                .endDate(request.endDate())
                .maxStudents(request.maxStudents())
                .status(initialStatus(request.startDate(), request.endDate()))
                .build();
        try {
            cohort = cohortRepository.saveAndFlush(cohort);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST,
                    "Mã lớp đã tồn tại.", HttpStatus.CONFLICT, exception);
        }

        auditLogService.logUserAction(
                userId,
                "TEACHER",
                "CREATE_LEARNING_COHORT",
                "LEARNING_COHORT",
                cohort.getId(),
                Map.of(),
                Map.of("code", cohort.getCode(), "courseId", course.getId().toString()),
                Map.of("organization", cohort.getOrganizationName()));
        return toResponse(cohort);
    }

    public CohortResponse updateStatus(UUID cohortId, CohortStatus status) {
        LearningCohort cohort = resolveOwnedCohortForUpdate(cohortId);
        CohortStatus before = cohort.getStatus();
        cohort.setStatus(status);
        cohortRepository.saveAndFlush(cohort);
        for (CohortMember member : cohort.getMembers().stream()
                .sorted(java.util.Comparator.comparing(m -> m.getStudent().getId())).toList()) {
            if (member.getStatus() == CohortMemberStatus.ACTIVE) synchronizeCohortAccess(member.getStudent(), cohort.getCourse());
        }
        auditLogService.logUserAction(
                currentUserService.getCurrentUserId(),
                "TEACHER",
                "UPDATE_COHORT_STATUS",
                "LEARNING_COHORT",
                cohort.getId(),
                Map.of("status", before.name()),
                Map.of("status", status.name()),
                Map.of("code", cohort.getCode()));
        return toResponse(cohort);
    }

    public CohortResponse addMember(UUID cohortId, AddCohortMemberRequest request) {
        LearningCohort cohort = resolveOwnedCohortForUpdate(cohortId);
        if (cohort.getStatus() == CohortStatus.ARCHIVED || cohort.getStatus() == CohortStatus.COMPLETED) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST,
                    "Không thể thêm học viên vào lớp đã kết thúc hoặc lưu trữ.", HttpStatus.CONFLICT);
        }
        long activeCount = memberRepository.countByCohort_IdAndStatus(
                cohort.getId(), CohortMemberStatus.ACTIVE);
        if (activeCount >= cohort.getMaxStudents()) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST,
                    "Lớp đã đủ số học viên tối đa.", HttpStatus.CONFLICT);
        }
        StudentProfile resolvedStudent = studentProfileRepository.findByUser_EmailIgnoreCase(
                        request.studentEmail().trim())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Email chưa có tài khoản học viên trên hệ thống.",
                        HttpStatus.NOT_FOUND));

        StudentProfile student = studentProfileRepository.findForUpdate(resolvedStudent.getId()).orElseThrow();

        CohortMember member = memberRepository.findByCohort_IdAndStudent_Id(cohortId, student.getId())
                .orElseGet(() -> CohortMember.builder()
                        .cohort(cohort)
                        .student(student)
                        .build());
        if (member.getId() != null && member.getStatus() == CohortMemberStatus.ACTIVE) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST,
                    "Học viên đã có trong lớp.", HttpStatus.CONFLICT);
        }
        member.setStatus(CohortMemberStatus.ACTIVE);
        member.setRemovedAt(null);
        memberRepository.saveAndFlush(member);
        synchronizeCohortAccess(student, cohort.getCourse());

        notificationService.createNotification(
                student.getUser().getId(),
                student.getUser().getEmail(),
                "Bạn đã được thêm vào lớp " + cohort.getName(),
                "SOFL đã cấp quyền học khóa \"" + cohort.getCourse().getTitle()
                        + "\" đến hết ngày " + cohort.getEndDate() + ".",
                NotificationTypes.COHORT_ENROLLMENT,
                "/student/cohorts/" + cohort.getId());
        auditLogService.logUserAction(
                currentUserService.getCurrentUserId(),
                "TEACHER",
                "ADD_COHORT_MEMBER",
                "LEARNING_COHORT",
                cohort.getId(),
                Map.of(),
                Map.of("studentId", student.getId().toString()),
                Map.of("code", cohort.getCode()));
        return getMine(cohortId);
    }

    public CohortResponse removeMember(UUID cohortId, UUID memberId) {
        LearningCohort cohort = resolveOwnedCohortForUpdate(cohortId);
        CohortMember member = cohort.getMembers().stream()
                .filter(item -> item.getId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Không tìm thấy học viên trong lớp.",
                        HttpStatus.NOT_FOUND));
        member.setStatus(CohortMemberStatus.REMOVED);
        member.setRemovedAt(Instant.now());
        memberRepository.saveAndFlush(member);
        synchronizeCohortAccess(member.getStudent(), cohort.getCourse());
        auditLogService.logUserAction(
                currentUserService.getCurrentUserId(),
                "TEACHER",
                "REMOVE_COHORT_MEMBER",
                "LEARNING_COHORT",
                cohort.getId(),
                Map.of("studentId", member.getStudent().getId().toString()),
                Map.of("memberStatus", CohortMemberStatus.REMOVED.name()),
                Map.of("code", cohort.getCode()));
        return getMine(cohortId);
    }

    private LearningCohort resolveOwnedCohort(UUID cohortId) {
        return cohortRepository.findByIdAndOwnerTeacher_User_Id(
                        cohortId, currentUserService.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Không tìm thấy lớp học.",
                        HttpStatus.NOT_FOUND));
    }

    private LearningCohort resolveOwnedCohortForUpdate(UUID cohortId) {
        LearningCohort cohort = cohortRepository.findForUpdate(cohortId)
                .orElseThrow(() -> new BusinessException(MessageCodes.COMMON_NOT_FOUND, "Không tìm thấy lớp học.", HttpStatus.NOT_FOUND));
        if (!cohort.getOwnerTeacher().getUser().getId().equals(currentUserService.getCurrentUserId())) {
            throw new BusinessException(MessageCodes.COMMON_NOT_FOUND, "Không tìm thấy lớp học.", HttpStatus.NOT_FOUND);
        }
        return cohort;
    }

    private void synchronizeCohortAccess(StudentProfile requestedStudent, Course course) {
        enrollmentAccessService.synchronizeCohorts(requestedStudent, course,
                memberRepository.findByStudent_IdAndCohort_Course_IdAndStatus(
                        requestedStudent.getId(), course.getId(), CohortMemberStatus.ACTIVE));
    }

    private CohortResponse toResponse(LearningCohort cohort) {
        List<UUID> studentIds = cohort.getMembers().stream()
                .map(member -> member.getStudent().getId())
                .toList();
        Map<UUID, Enrollment> enrollmentByStudent = studentIds.isEmpty()
                ? Map.of()
                : enrollmentRepository.findByCourse_IdAndStudent_IdIn(
                                cohort.getCourse().getId(), studentIds)
                        .stream()
                        .collect(Collectors.toMap(
                                enrollment -> enrollment.getStudent().getId(),
                                Function.identity()));
        List<CohortResponse.MemberResponse> members = cohort.getMembers().stream()
                .map(member -> {
                    Enrollment enrollment = enrollmentByStudent.get(member.getStudent().getId());
                    return new CohortResponse.MemberResponse(
                            member.getId(),
                            member.getStudent().getId(),
                            member.getStudent().getUser().getFullName(),
                            member.getStudent().getUser().getEmail(),
                            member.getStatus(),
                            enrollment == null ? null : enrollment.getStatus(),
                            member.getJoinedAt(),
                            enrollment == null ? null : enrollment.getExpiresAt());
                })
                .toList();
        long activeStudentCount = cohort.getMembers().stream()
                .filter(member -> member.getStatus() == CohortMemberStatus.ACTIVE)
                .count();
        Course course = cohort.getCourse();
        return new CohortResponse(
                cohort.getId(),
                course.getId(),
                course.getTitle(),
                course.getLearningLanguage() == null ? LearningLanguage.JAPANESE : course.getLearningLanguage(),
                course.getProficiencyFramework() == null ? ProficiencyFramework.JLPT : course.getProficiencyFramework(),
                course.getProficiencyLevel() == null && course.getJlptLevel() != null
                        ? course.getJlptLevel().name()
                        : course.getProficiencyLevel(),
                cohort.getName(),
                cohort.getCode(),
                cohort.getOrganizationName(),
                cohort.getBranchName(),
                cohort.getStartDate(),
                cohort.getEndDate(),
                cohort.getMaxStudents(),
                cohort.getStatus(),
                activeStudentCount,
                members,
                cohort.getCreatedAt(),
                cohort.getUpdatedAt());
    }

    private String normalizeCode(String requestedCode, String name) {
        String code = StringUtils.hasText(requestedCode)
                ? requestedCode
                : name.replaceAll("[^A-Za-z0-9]", "").substring(
                        0, Math.min(6, name.replaceAll("[^A-Za-z0-9]", "").length()))
                        + "-" + UUID.randomUUID().toString().substring(0, 6);
        code = code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9-]", "-");
        code = code.replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (!code.matches("[A-Z0-9][A-Z0-9-]{3,31}")) {
            throw new BusinessException(MessageCodes.VALIDATION_FAILED,
                    "Mã lớp cần từ 4-32 ký tự, chỉ gồm chữ in hoa, số và dấu gạch ngang.");
        }
        return code;
    }

    private CohortStatus initialStatus(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        if (today.isBefore(startDate)) return CohortStatus.PLANNED;
        if (today.isAfter(endDate)) return CohortStatus.COMPLETED;
        return CohortStatus.ACTIVE;
    }

    private Instant cohortAccessExpiry(LearningCohort cohort) {
        return cohort.getEndDate().plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
