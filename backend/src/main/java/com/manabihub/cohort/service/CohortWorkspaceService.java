package com.manabihub.cohort.service;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.cohort.domain.*;
import com.manabihub.cohort.dto.CohortWorkspaceDto.*;
import com.manabihub.cohort.repository.*;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.notification.NotificationTypes;
import com.manabihub.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional
public class CohortWorkspaceService {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long MAX_AUDIO_BYTES = 8L * 1024 * 1024;
    private final LearningCohortRepository cohorts;
    private final CohortMemberRepository members;
    private final CohortAssignmentRepository assignments;
    private final CohortSubmissionRepository submissions;
    private final CurrentUserService currentUser;
    private final NotificationService notifications;
    private final AuditLogService audit;
    private final CohortAudioValidator audioValidator;
    private final CohortReportQueryService reportQuery;
    @Value("${manabihub.cohort.audio-storage-root:storage/cohort-audio}")
    private String audioStorageRoot;

    @Transactional(readOnly = true)
    public List<ClassSummary> studentClasses() {
        return reportQuery.studentClasses(currentUser.getCurrentUserId());
    }

    @Transactional(readOnly = true)
    public Workspace teacherWorkspace(UUID cohortId) { return workspace(owned(cohortId, false), null); }

    @Transactional(readOnly = true)
    public Workspace studentWorkspace(UUID cohortId) {
        LearningCohort cohort = found(cohortId, false);
        return workspace(cohort, studentMember(cohort));
    }

    public Workspace createAssignment(UUID cohortId, CreateAssignment request) {
        LearningCohort cohort = owned(cohortId, true);
        ensureOpen(cohort);
        Instant end = cohort.getEndDate().plusDays(1).atStartOfDay(VN).toInstant();
        Instant start = cohort.getStartDate().atStartOfDay(VN).toInstant();
        if (!request.dueAt().isAfter(Instant.now()) || request.dueAt().isBefore(start)
                || request.acceptUntil().isBefore(request.dueAt()) || !request.acceptUntil().isBefore(end)) {
            throw bad("Hạn nộp phải ở tương lai, trong thời gian lớp; hạn nộp lại phải từ hạn nộp đến trước khi lớp hết quyền học.");
        }
        if (request.title().isBlank() || request.instructions().isBlank()) throw bad("Nhập tên và hướng dẫn bài tập.");
        CohortAssignment assignment = assignments.saveAndFlush(CohortAssignment.builder()
                .cohort(cohort).title(request.title().trim()).instructions(request.instructions().trim())
                .type(request.type()).dueAt(request.dueAt()).acceptUntil(request.acceptUntil())
                .maxAttempts(request.maxAttempts()).maxAudioSeconds(request.maxAudioSeconds()).build());
        for (CohortMember member : activeMembers(cohort)) {
            notifyStudent(member, "Bài tập mới: " + assignment.getTitle(),
                    "Lớp " + cohort.getName() + ": hạn nộp " + request.dueAt().atZone(VN).toLocalDateTime(), NotificationTypes.COHORT_ASSIGNMENT);
        }
        log("CREATE_COHORT_ASSIGNMENT", cohortId, Map.of("assignmentId", assignment.getId().toString()));
        return workspace(cohort, null);
    }

    public Workspace closeAssignment(UUID cohortId, UUID assignmentId) {
        LearningCohort cohort = owned(cohortId, true);
        CohortAssignment assignment = assignmentIn(cohortId, assignmentId);
        assignment.setClosed(true);
        assignments.saveAndFlush(assignment);
        log("CLOSE_COHORT_ASSIGNMENT", cohortId, Map.of("assignmentId", assignmentId.toString()));
        return workspace(cohort, null);
    }

    public Workspace updatePackage(UUID cohortId, PackageSettings request) {
        LearningCohort cohort = owned(cohortId, true);
        List<CohortSubmission> attempts = submissions.findByAssignment_Cohort_IdOrderBySubmittedAtDesc(cohortId);
        List<CohortAssignment> tasks = assignments.findByCohort_IdOrderByDueAtAsc(cohortId);
        long mostUsed = cohort.getMembers().stream().mapToLong(m -> {
            List<CohortSubmission> own = attempts.stream().filter(s -> s.getMember().getId().equals(m.getId())).toList();
            return own.size() + reservedRevisions(cohort, tasks, own);
        }).max().orElse(0);
        if (request.feedbackQuotaPerStudent() < mostUsed) throw bad("Định mức mới không được thấp hơn số lượt đã dùng.");
        cohort.setFeedbackQuotaPerStudent(request.feedbackQuotaPerStudent());
        cohort.setReviewSlaHours(request.reviewSlaHours());
        cohorts.saveAndFlush(cohort);
        log("UPDATE_COHORT_FEEDBACK_PACKAGE", cohortId, Map.of("quota", request.feedbackQuotaPerStudent(), "slaHours", request.reviewSlaHours()));
        return workspace(cohort, null);
    }

    public Workspace submit(UUID cohortId, UUID assignmentId, String content, Integer reportedSeconds, MultipartFile audio) {
        LearningCohort cohort = found(cohortId, true);
        CohortMember member = studentMember(cohort);
        ensureOpen(cohort);
        LocalDate today = LocalDate.now(VN);
        if (today.isBefore(cohort.getStartDate()) || today.isAfter(cohort.getEndDate())) throw conflict("Lớp chưa bắt đầu hoặc đã kết thúc.");
        CohortAssignment assignment = assignmentIn(cohortId, assignmentId);
        if (assignment.isClosed() || Instant.now().isAfter(assignment.getAcceptUntil())) throw conflict("Bài tập đã đóng nhận bài.");
        List<CohortSubmission> history = submissions.findByAssignment_IdAndMember_IdOrderByAttemptDesc(assignmentId, member.getId());
        if (!history.isEmpty()) {
            CohortSubmission latest = history.getFirst();
            if (latest.getReviewedAt() == null) throw conflict("Bài đang chờ giáo viên chữa; hãy chờ phản hồi trước khi nộp lại.");
            if (!latest.isRevisionRequested()) throw conflict("Bài đã được hoàn tất; giáo viên chưa yêu cầu nộp lại.");
        }
        if (history.size() >= assignment.getMaxAttempts()) throw conflict("Đã đạt số lần nộp tối đa của bài tập.");
        List<CohortSubmission> own = submissions.findByMember_IdOrderBySubmittedAtDesc(member.getId());
        int reserved = reservedRevisions(cohort, assignments.findByCohort_IdOrderByDueAtAsc(cohortId), own);
        boolean usingReservation = !history.isEmpty() && history.getFirst().isRevisionRequested();
        if (own.size() + reserved - (usingReservation ? 1 : 0) >= cohort.getFeedbackQuotaPerStudent())
            throw conflict("Các lượt chữa đã dùng hoặc được giữ cho bài cần sửa. Liên hệ giáo viên để điều chỉnh gói.");
        CohortSubmission submission = new CohortSubmission();
        submission.setId(UUID.randomUUID()); submission.setAssignment(assignment); submission.setMember(member);
        submission.setAttempt(history.size() + 1);
        submission.setReviewDueAt(Instant.now().plus(Duration.ofHours(cohort.getReviewSlaHours())));
        if (assignment.getType() == AssignmentType.WRITING) {
            if (!StringUtils.hasText(content) || content.trim().length() > 10000) throw bad("Bài viết cần từ 1 đến 10.000 ký tự.");
            if (audio != null && !audio.isEmpty()) throw bad("Bài viết không nhận tệp ghi âm.");
            submission.setContent(content.trim());
        } else {
            if (reportedSeconds == null || reportedSeconds < 1 || reportedSeconds > assignment.getMaxAudioSeconds()) throw bad("Thời lượng ghi âm vượt giới hạn của bài tập.");
            if (content != null && content.length() > 2000) throw bad("Ghi chú không được quá 2.000 ký tự.");
            storeAudio(submission, audio, assignment.getMaxAudioSeconds());
            submission.setContent(StringUtils.hasText(content) ? content.trim() : null);
            submission.setReportedAudioSeconds(reportedSeconds);
        }
        submissions.saveAndFlush(submission);
        var teacher = cohort.getOwnerTeacher().getUser();
        notifications.createNotification(teacher.getId(), teacher.getEmail(), "Bài tập mới cần chữa",
                member.getStudent().getUser().getFullName() + " đã nộp " + assignment.getTitle(),
                NotificationTypes.COHORT_SUBMITTED, "/teacher/cohorts/" + cohortId + "/workspace");
        log("SUBMIT_COHORT_ASSIGNMENT", cohortId, Map.of("submissionId", submission.getId().toString(), "attempt", submission.getAttempt()));
        return workspace(cohort, member);
    }

    public Workspace review(UUID cohortId, UUID submissionId, Feedback request) {
        LearningCohort cohort = owned(cohortId, true);
        CohortSubmission submission = submissions.findForUpdate(submissionId).orElseThrow(this::notFound);
        if (!submission.getAssignment().getCohort().getId().equals(cohortId)) throw notFound();
        if (submission.getReviewedAt() != null) throw conflict("Bài này đã được chữa. Phản hồi được giữ nguyên trong lịch sử.");
        if (request.revisionRequested() && submission.getAttempt() >= submission.getAssignment().getMaxAttempts()) throw bad("Bài đã hết số lượt nộp; hãy hoàn tất bài với hướng dẫn cải thiện.");
        List<CohortSubmission> own = submissions.findByMember_IdOrderBySubmittedAtDesc(submission.getMember().getId());
        int reserved = reservedRevisions(cohort, assignments.findByCohort_IdOrderByDueAtAsc(cohortId), own);
        if (request.revisionRequested() && (submission.getMember().getStatus() != CohortMemberStatus.ACTIVE
                || LocalDate.now(VN).isBefore(cohort.getStartDate()) || LocalDate.now(VN).isAfter(cohort.getEndDate())
                || submission.getAssignment().isClosed()
                || Instant.now().isAfter(submission.getAssignment().getAcceptUntil())
                || cohort.getStatus() == CohortStatus.COMPLETED || cohort.getStatus() == CohortStatus.ARCHIVED
                || own.size() + reserved >= cohort.getFeedbackQuotaPerStudent())) {
            throw bad("Chưa thể yêu cầu nộp lại: lớp/bài đã đóng, hết hạn hoặc hết lượt chữa. Hãy hoàn tất phản hồi hoặc bổ sung định mức trước.");
        }
        submission.setFeedback(request.feedback().trim()); submission.setScore(request.score());
        submission.setRevisionRequested(request.revisionRequested()); submission.setReviewMinutes(request.reviewMinutes());
        submission.setReviewedAt(Instant.now()); submission.setReviewedBy(currentUser.getCurrentUserId());
        submissions.saveAndFlush(submission);
        notifyStudent(submission.getMember(), "Giáo viên đã chữa bài " + submission.getAssignment().getTitle(),
                request.revisionRequested() ? "Xem phản hồi và nộp lại trước hạn nhận bài." : "Xem điểm và hướng dẫn cải thiện trong lớp học.", NotificationTypes.COHORT_FEEDBACK);
        log("REVIEW_COHORT_SUBMISSION", cohortId, Map.of("submissionId", submissionId.toString(), "score", request.score()));
        return workspace(cohort, null);
    }

    public Workspace remind(UUID cohortId, UUID memberId) {
        LearningCohort cohort = owned(cohortId, true);
        String blockReason = submissionBlockReason(cohort);
        if (blockReason != null) throw conflict(blockReason);
        CohortMember member = activeMembers(cohort).stream().filter(m -> m.getId().equals(memberId)).findFirst().orElseThrow(this::notFound);
        if (member.getLastReminderAt() != null && member.getLastReminderAt().plus(Duration.ofHours(24)).isAfter(Instant.now())) throw conflict("Học viên đã được nhắc trong 24 giờ qua.");
        Workspace workspace = workspace(cohort, null);
        MemberProgress progress = workspace.members().stream().filter(m -> m.memberId().equals(memberId)).findFirst().orElseThrow(this::notFound);
        if (progress.overdueAssignments() + progress.awaitingRevision() == 0) throw bad("Học viên không có bài quá hạn hoặc cần nộp lại.");
        notifyStudent(member, "Lớp " + cohort.getName() + " cần bạn hoàn thành bài tập",
                "Bạn còn " + progress.overdueAssignments() + " bài quá hạn và " + progress.awaitingRevision() + " bài cần sửa. Hãy xem lớp học hoặc liên hệ giáo viên.", NotificationTypes.COHORT_REMINDER);
        member.setLastReminderAt(Instant.now()); members.saveAndFlush(member);
        log("REMIND_COHORT_MEMBER", cohortId, Map.of("memberId", memberId.toString()));
        return workspace(cohort, null);
    }

    public record AudioDownload(FileSystemResource resource, String mimeType) {}
    @Transactional(readOnly = true)
    public AudioDownload audio(UUID submissionId, boolean teacher) {
        CohortSubmission submission = submissions.findById(submissionId).orElseThrow(this::notFound);
        UUID cohortId = submission.getAssignment().getCohort().getId();
        if (teacher) owned(cohortId, false);
        else if (!submission.getMember().getStudent().getUser().getId().equals(currentUser.getCurrentUserId())) throw notFound();
        if (submission.getAudioPath() == null) throw notFound();
        Path path = Path.of(submission.getAudioPath()).toAbsolutePath().normalize();
        if (!path.startsWith(Path.of(audioStorageRoot).toAbsolutePath().normalize()) || !Files.isRegularFile(path)) throw notFound();
        return new AudioDownload(new FileSystemResource(path), submission.getAudioMimeType());
    }

    @Transactional(readOnly = true)
    public ReportPage reports(boolean admin, String organization, String branch, LocalDate from, LocalDate to, int page, int size) {
        return reportQuery.reports(admin ? null : currentUser.getCurrentUserId(), organization, branch, from, to, page, size);
    }

    private Workspace workspace(LearningCohort cohort, CohortMember viewer) {
        List<CohortAssignment> tasks = assignments.findByCohort_IdOrderByDueAtAsc(cohort.getId());
        List<CohortSubmission> attempts = viewer == null ? submissions.findByAssignment_Cohort_IdOrderBySubmittedAtDesc(cohort.getId())
                : submissions.findByMember_IdOrderBySubmittedAtDesc(viewer.getId());
        List<CohortMember> roster = viewer == null ? activeMembers(cohort) : List.of(viewer);
        Map<UUID, List<CohortSubmission>> byMember = attempts.stream().collect(Collectors.groupingBy(s -> s.getMember().getId()));
        List<MemberProgress> progress = roster.stream().map(m -> progress(cohort, m, tasks, byMember.getOrDefault(m.getId(), List.of()))).toList();
        int used = attempts.size();
        int remaining = progress.stream().mapToInt(MemberProgress::feedbackRemaining).sum();
        return new Workspace(cohort.getId(), cohort.getName(), cohort.getCode(), cohort.getCourse().getId(),
                cohort.getCourse().getLearningLanguage(), cohort.getStatus(), cohort.getStartDate(), cohort.getEndDate(),
                submissionBlockReason(cohort) == null, submissionBlockReason(cohort), cohort.getFeedbackQuotaPerStudent(), cohort.getReviewSlaHours(), used, remaining,
                tasks.stream().map(t -> new Assignment(t.getId(), t.getTitle(), t.getInstructions(), t.getType(), t.getDueAt(), t.getAcceptUntil(), t.getMaxAttempts(), t.getMaxAudioSeconds(), t.isClosed())).toList(),
                attempts.stream().map(s -> new Submission(s.getId(), s.getAssignment().getId(), s.getMember().getId(), s.getMember().getStudent().getUser().getFullName(),
                         s.getAttempt(), s.getContent(), s.getAudioPath() != null, s.getReportedAudioSeconds(), s.getVerifiedAudioSeconds(), s.getSubmittedAt(), s.getReviewDueAt(),
                        s.getFeedback(), s.getScore(), s.isRevisionRequested(), s.getReviewMinutes(), s.getReviewedAt())).toList(), progress);
    }

    private MemberProgress progress(LearningCohort cohort, CohortMember member, List<CohortAssignment> tasks, List<CohortSubmission> attempts) {
        List<CohortSubmission> own = attempts;
        Map<UUID, CohortSubmission> latest = latest(own);
        int overdue = (int) tasks.stream().filter(t -> isDueFor(t, member) && !latest.containsKey(t.getId())).count();
        int revisions = (int) tasks.stream().filter(t -> !t.isClosed()).map(t -> latest.get(t.getId()))
                .filter(Objects::nonNull).filter(s -> s.getReviewedAt() != null && s.isRevisionRequested()).count();
        int reserved = reservedRevisions(cohort, tasks, own);
        return new MemberProgress(member.getId(), member.getStudent().getUser().getFullName(), member.getStudent().getUser().getEmail(), overdue,
                revisions, latest.size(), own.size(), Math.max(0, cohort.getFeedbackQuotaPerStudent() - own.size() - reserved), reserved, latestAverage(own),
                own.stream().map(CohortSubmission::getSubmittedAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null), member.getLastReminderAt());
    }

    private boolean isDueFor(CohortAssignment task, CohortMember member) {
        return !task.getDueAt().isAfter(Instant.now()) && !task.getDueAt().isBefore(member.getJoinedAt());
    }
    private String submissionBlockReason(LearningCohort cohort) {
        if (cohort.getStatus() == CohortStatus.COMPLETED || cohort.getStatus() == CohortStatus.ARCHIVED) return "Lớp đã kết thúc hoặc được lưu trữ.";
        LocalDate today = LocalDate.now(VN);
        if (today.isBefore(cohort.getStartDate())) return "Lớp chưa đến ngày bắt đầu.";
        if (today.isAfter(cohort.getEndDate())) return "Lớp đã hết thời gian nhận bài.";
        return null;
    }
    private int reservedRevisions(LearningCohort cohort, List<CohortAssignment> tasks, List<CohortSubmission> own) {
        if (submissionBlockReason(cohort) != null) return 0;
        Map<UUID, CohortSubmission> latest = latest(own);
        Instant now = Instant.now();
        return (int) tasks.stream().filter(t -> !t.isClosed() && !now.isAfter(t.getAcceptUntil())).map(t -> latest.get(t.getId()))
                .filter(Objects::nonNull).filter(s -> s.getReviewedAt() != null && s.isRevisionRequested()).count();
    }
    private Map<UUID, CohortSubmission> latest(List<CohortSubmission> attempts) {
        return attempts.stream().collect(Collectors.toMap(s -> s.getAssignment().getId(), s -> s, (a, b) -> a.getAttempt() > b.getAttempt() ? a : b));
    }
    private Double latestAverage(List<CohortSubmission> attempts) {
        // Latest reviewed attempt for each learner/assignment, excluding superseded grades.
        Map<String, CohortSubmission> latestReviewed = attempts.stream().filter(s -> s.getScore() != null)
                .collect(Collectors.toMap(s -> s.getMember().getId() + ":" + s.getAssignment().getId(), s -> s,
                        (a, b) -> a.getAttempt() > b.getAttempt() ? a : b));
        return latestReviewed.isEmpty() ? null : latestReviewed.values().stream().mapToInt(CohortSubmission::getScore).average().orElse(0);
    }
    private List<CohortMember> activeMembers(LearningCohort cohort) { return cohort.getMembers().stream().filter(m -> m.getStatus() == CohortMemberStatus.ACTIVE).toList(); }
    private CohortMember studentMember(LearningCohort cohort) {
        UUID userId = currentUser.getCurrentUserId();
        return activeMembers(cohort).stream().filter(m -> m.getStudent().getUser().getId().equals(userId)).findFirst().orElseThrow(this::notFound);
    }
    private LearningCohort found(UUID id, boolean lock) { return (lock ? cohorts.findForUpdate(id) : cohorts.findById(id)).orElseThrow(this::notFound); }
    private LearningCohort owned(UUID id, boolean lock) {
        LearningCohort cohort = found(id, lock);
        if (!cohort.getOwnerTeacher().getUser().getId().equals(currentUser.getCurrentUserId())) throw notFound();
        return cohort;
    }
    private CohortAssignment assignmentIn(UUID cohortId, UUID assignmentId) {
        CohortAssignment assignment = assignments.findById(assignmentId).orElseThrow(this::notFound);
        if (!assignment.getCohort().getId().equals(cohortId)) throw notFound();
        return assignment;
    }
    private void ensureOpen(LearningCohort cohort) {
        if (cohort.getStatus() == CohortStatus.COMPLETED || cohort.getStatus() == CohortStatus.ARCHIVED) throw conflict("Lớp đã kết thúc hoặc được lưu trữ.");
    }
    private void notifyStudent(CohortMember member, String title, String message, String type) {
        var student = member.getStudent().getUser();
        notifications.createNotification(student.getId(), student.getEmail(), title, message, type, "/student/cohorts/" + member.getCohort().getId());
    }
    private void log(String action, UUID cohortId, Map<String, Object> details) {
        audit.logUserAction(currentUser.getCurrentUserId(), "COHORT_USER", action, "LEARNING_COHORT", cohortId, Map.of(), details, Map.of());
    }
    private BusinessException notFound() { return new BusinessException("COHORT_NOT_FOUND", "Không tìm thấy lớp học hoặc bài nộp của bạn.", HttpStatus.NOT_FOUND); }
    private BusinessException bad(String message) { return new BusinessException("COHORT_INVALID", message, HttpStatus.BAD_REQUEST); }
    private BusinessException conflict(String message) { return new BusinessException("COHORT_CONFLICT", message, HttpStatus.CONFLICT); }

    private void storeAudio(CohortSubmission submission, MultipartFile audio, int maxSeconds) {
        if (audio == null || audio.isEmpty() || audio.getSize() > MAX_AUDIO_BYTES) throw bad("Chọn bản ghi âm không quá 8 MB.");
        Path destination = null;
        try {
            byte[] bytes = audio.getBytes();
            if (bytes.length > MAX_AUDIO_BYTES || bytes.length < 12) throw bad("Tệp ghi âm không hợp lệ.");
            String mime = audio.getContentType() == null ? "" : audio.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
            String head = new String(bytes, 0, Math.min(bytes.length, 16), StandardCharsets.ISO_8859_1);
            String extension;
            if (mime.equals("audio/webm") && (bytes[0] & 255) == 0x1a && (bytes[1] & 255) == 0x45 && (bytes[2] & 255) == 0xdf && (bytes[3] & 255) == 0xa3) extension = "webm";
            else if (mime.equals("audio/ogg") && head.startsWith("OggS")) extension = "ogg";
            else if ((mime.equals("audio/mp4") || mime.equals("audio/x-m4a")) && head.substring(4, 8).equals("ftyp")) { extension = "m4a"; mime = "audio/mp4"; }
            else if ((mime.equals("audio/wav") || mime.equals("audio/x-wav")) && head.startsWith("RIFF") && head.substring(8, 12).equals("WAVE")) { extension = "wav"; mime = "audio/wav"; }
            else if (mime.equals("audio/mpeg") && (head.startsWith("ID3") || ((bytes[0] & 255) == 255 && (bytes[1] & 0xe0) == 0xe0))) extension = "mp3";
            else throw bad("Nhận tệp âm thanh WebM, OGG, M4A, WAV hoặc MP3 đúng định dạng.");
            Path root = Path.of(audioStorageRoot).toAbsolutePath().normalize();
            Files.createDirectories(root);
            destination = root.resolve(submission.getId() + "." + extension);
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW);
            Path savedPath = destination;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) deleteAudio(savedPath);
                }
            });
            submission.setVerifiedAudioSeconds(audioValidator.verify(destination, extension, maxSeconds));
            submission.setAudioPath(destination.toString()); submission.setAudioMimeType(mime); submission.setAudioBytes((long) bytes.length);
            submission.setAudioSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (BusinessException exception) { throw exception; }
        catch (Exception exception) {
            if (destination != null) deleteAudio(destination);
            throw new BusinessException("COHORT_AUDIO_STORAGE", "Chưa lưu được bản ghi âm. Hãy thử lại.", HttpStatus.INTERNAL_SERVER_ERROR, exception);
        }
    }
    private void deleteAudio(Path path) { try { Files.deleteIfExists(path); } catch (IOException ignored) { /* Retry orphan cleanup operationally. */ } }
}
