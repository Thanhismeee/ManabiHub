package com.manabihub.course.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.course.dto.response.ValidationError;
import com.manabihub.course.dto.response.ValidationResultResponse;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.CourseLearningGoal;
import com.manabihub.course.entity.CourseModule;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.revision.CourseEditDraftService;
import com.manabihub.course.service.CourseValidationService;
import com.manabihub.finaltest.entity.FinalTest;
import com.manabihub.finaltest.entity.FinalTestChoice;
import com.manabihub.finaltest.entity.FinalTestQuestion;
import com.manabihub.finaltest.repository.FinalTestRepository;
import com.manabihub.identity.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseValidationServiceImpl implements CourseValidationService {

    private static final String INTERNAL_THUMBNAIL_PATH_PREFIX = "/uploads/course-thumbnails/";

    private final CourseRepository courseRepository;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;
    private final FinalTestRepository finalTestRepository;
    private final CourseEditDraftService courseEditDraftService;

    @Override
    @Transactional(readOnly = true)
    public ValidationResultResponse validateCourse(UUID courseId) {
        Course course = resolveCourseForValidation(courseId);

        if (course.getTeacher() == null
                || course.getTeacher().getUser() == null
                || !course.getTeacher().getUser().getId().equals(currentUserService.getCurrentUserId())) {
            throw new SecurityException("You do not have permission to validate this course");
        }

        return validateResolvedCourse(course);
    }

    @Override
    @Transactional(readOnly = true)
    public ValidationResultResponse validateCourseForReview(UUID courseId) {
        return validateResolvedCourse(resolveCourseForValidation(courseId));
    }

    private Course resolveCourseForValidation(UUID courseId) {
        Course persistedCourse = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        return courseEditDraftService.resolveEditableCourse(persistedCourse);
    }

    private ValidationResultResponse validateResolvedCourse(Course course) {
        List<ValidationError> errors = new ArrayList<>();

        validateMetadata(course, errors);
        validateGoals(course, errors);
        validateHierarchy(course, errors);
        validateContentBlocks(course, errors);
        validateFinalTest(course, errors);

        return new ValidationResultResponse(errors.isEmpty(), errors);
    }

    private void validateMetadata(Course course, List<ValidationError> errors) {
        boolean hasProficiencyLevel = StringUtils.hasText(course.getProficiencyLevel())
                || course.getJlptLevel() != null;
        if (!StringUtils.hasText(course.getTitle())
                || course.getLearningLanguage() == null
                || course.getProficiencyFramework() == null
                || !hasProficiencyLevel
                || !StringUtils.hasText(course.getIntroduction())
                || !StringUtils.hasText(course.getOutcomes())
                || !StringUtils.hasText(course.getThumbnailUrl())
                || !StringUtils.hasText(course.getCategory())
                || course.getPrice() == null) {
            errors.add(new ValidationError("MSG-COURSE-004", "Sản phẩm chưa đáp ứng điều kiện gửi duyệt.", "error"));
        } else {
            int titleLen = course.getTitle().trim().length();
            if (titleLen < 10 || titleLen > 100) {
                errors.add(
                        new ValidationError("MSG-COURSE-020", "Tiêu đề khóa học phải từ 10 đến 100 ký tự.", "error"));
            }
            if (!isValidThumbnailUrl(course.getThumbnailUrl())) {
                errors.add(new ValidationError("MSG-COURSE-021", "Đường dẫn ảnh thu nhỏ không hợp lệ.", "error"));
            }
        }

        if (course.getPrice() != null) {
            double price = course.getPrice().doubleValue();
            if (price < 0) {
                errors.add(new ValidationError("MSG-COURSE-003",
                        "Giá sản phẩm không được thấp hơn mức tối thiểu của nền tảng.", "error"));
            } else if (price > 0 && price < 10000) {
                errors.add(new ValidationError("MSG-COURSE-003",
                        "Giá sản phẩm nếu có thu phí phải lớn hơn hoặc bằng 10,000 VND.", "error"));
            }
        }
    }

    private boolean isValidThumbnailUrl(String thumbnailUrl) {
        String normalizedUrl = thumbnailUrl.trim();
        if (normalizedUrl.regionMatches(true, 0, "http://", 0, "http://".length())
                || normalizedUrl.regionMatches(true, 0, "https://", 0, "https://".length())) {
            return true;
        }

        if (!normalizedUrl.startsWith(INTERNAL_THUMBNAIL_PATH_PREFIX)) {
            return false;
        }

        String fileName = normalizedUrl.substring(INTERNAL_THUMBNAIL_PATH_PREFIX.length());
        return StringUtils.hasText(fileName)
                && !fileName.contains("/")
                && !fileName.contains("\\")
                && !fileName.contains("..");
    }

    private void validateGoals(Course course, List<ValidationError> errors) {
        List<CourseLearningGoal> goals = course.getLearningGoals();
        if (goals == null || goals.size() < 4) {
            errors.add(new ValidationError("MSG-GOAL-001", "Vui lòng nhập tối thiểu 4 mục tiêu học tập.", "error"));
        }

        if (goals != null) {
            Set<String> uniqueGoals = new HashSet<>();
            for (CourseLearningGoal goal : goals) {
                String text = goal.getGoalText();
                if (!StringUtils.hasText(text) || text.length() > 160) {
                    errors.add(new ValidationError("MSG-GOAL-002",
                            "Mỗi mục tiêu học tập không được vượt quá 160 ký tự.", "error"));
                    break;
                }
                if (!uniqueGoals.add(text.trim().toLowerCase())) {
                    errors.add(new ValidationError("MSG-GOAL-005",
                            "Phát hiện mục tiêu học tập bị trùng lặp. Vui lòng kiểm tra lại.", "error"));
                    break;
                }
            }
        }

        if (!StringUtils.hasText(course.getPrerequisites())) {
            errors.add(new ValidationError("MSG-GOAL-003",
                    "Vui lòng nhập yêu cầu đầu vào hoặc chọn Không yêu cầu đầu vào.", "error"));
        }

        if (!StringUtils.hasText(course.getTargetStudents())) {
            errors.add(new ValidationError("MSG-GOAL-004",
                    "Vui lòng mô tả đối tượng học viên phù hợp với sản phẩm này.", "error"));
        }
    }

    private void validateHierarchy(Course course, List<ValidationError> errors) {
        List<CourseModule> modules = course.getModules();
        if (modules == null || modules.isEmpty()) {
            errors.add(new ValidationError("MSG-COURSE-015",
                    "Video Course cần có tối thiểu 5 bài học trước khi gửi duyệt.", "error"));
            errors.add(new ValidationError("MSG-COURSE-016",
                    "Video Course cần có tổng thời lượng video tối thiểu 30 phút trước khi gửi duyệt.", "error"));
            return;
        }

        int totalLessons = 0;
        int totalVideoDuration = 0;

        for (CourseModule module : modules) {
            if (module.getBlocks() == null || module.getBlocks().isEmpty()) {
                errors.add(new ValidationError("MSG-COURSE-017",
                        "Không được phép có Module rỗng (Module chưa có bài học nào).", "error"));
            } else {
                totalLessons += module.getBlocks().size();
                for (LessonBlock block : module.getBlocks()) {
                    if (block.getType() == LessonBlockType.VIDEO && block.getDurationMinutes() != null) {
                        totalVideoDuration += block.getDurationMinutes();
                    }
                }
            }
        }

        if (totalLessons < 5) {
            errors.add(new ValidationError("MSG-COURSE-015",
                    "Video Course cần có tối thiểu 5 bài học trước khi gửi duyệt.", "error"));
        }

        if (totalVideoDuration < 30) {
            errors.add(new ValidationError("MSG-COURSE-016",
                    "Video Course cần có tổng thời lượng video tối thiểu 30 phút trước khi gửi duyệt.", "error"));
        }
    }

    private void validateContentBlocks(Course course, List<ValidationError> errors) {
        List<CourseModule> modules = course.getModules();
        if (modules == null || modules.isEmpty()) {
            return;
        }

        boolean hasLanguagePracticeEvidence = false;
        List<LessonBlock> allBlocks = new ArrayList<>();
        for (CourseModule module : modules) {
            if (module.getBlocks() != null) {
                allBlocks.addAll(module.getBlocks());
            }
        }

        if (allBlocks.isEmpty()) {
            return;
        }

        for (int i = 0; i < allBlocks.size(); i++) {
            LessonBlock block = allBlocks.get(i);

            if (block.getType() == LessonBlockType.QUIZ
                    || block.getType() == LessonBlockType.FLASHCARD
                    || block.getType() == LessonBlockType.WRITING) {
                hasLanguagePracticeEvidence = true;
            }

            if (block.getType() == LessonBlockType.VIDEO
                    && block.getDurationMinutes() != null
                    && block.getDurationMinutes() > 15
                    && !hasInteractionAfter(allBlocks, i)) {
                errors.add(new ValidationError("MSG-COURSE-009",
                        "Video vượt quá 15 phút nhưng không có Quiz/Flashcard/Writing ngay sau.", "error"));
            }

            validateQuizBlock(block, errors);
            validateFlashcardBlock(block, errors);
            validateWritingBlock(block, errors);
        }

        if (!hasLanguagePracticeEvidence) {
            errors.add(new ValidationError("MSG-COURSE-012",
                    "Bài học cần có hoạt động luyện tập ngôn ngữ như quiz, flashcard hoặc bài viết.", "error"));
        }
    }

    private boolean hasInteractionAfter(List<LessonBlock> allBlocks, int currentIndex) {
        if (currentIndex + 1 >= allBlocks.size()) {
            return false;
        }
        LessonBlockType nextType = allBlocks.get(currentIndex + 1).getType();
        return nextType == LessonBlockType.QUIZ
                || nextType == LessonBlockType.FLASHCARD
                || nextType == LessonBlockType.WRITING;
    }

    private void validateQuizBlock(LessonBlock block, List<ValidationError> errors) {
        if (block.getType() != LessonBlockType.QUIZ) {
            return;
        }

        if (!StringUtils.hasText(block.getQuizAnswer())) {
            errors.add(
                    new ValidationError("MSG-COURSE-013", "Câu hỏi quiz cần có đáp án đúng trước khi lưu.", "error"));
            return;
        }

        if (!StringUtils.hasText(block.getQuizOptionsJson())) {
            errors.add(new ValidationError(
                    "MSG-COURSE-013",
                    "Câu hỏi quiz cần có danh sách lựa chọn trước khi lưu.",
                    "error"
            ));
            return;
        }

        try {
            JsonNode options = objectMapper.readTree(block.getQuizOptionsJson());
            boolean answerMatches = options != null
                    && options.isArray()
                    && matchesQuizAnswer(options, block.getQuizAnswer());
            if (!answerMatches) {
                errors.add(new ValidationError("MSG-COURSE-013",
                        "Đáp án đúng không khớp với bất kỳ lựa chọn nào trong danh sách.", "error"));
            }
        } catch (Exception e) {
            log.warn("Failed to parse quiz options JSON for block {}", block.getId(), e);
            errors.add(new ValidationError(
                    "MSG-COURSE-013",
                    "Danh sách lựa chọn quiz không hợp lệ.",
                    "error"
            ));
        }
    }

    private boolean matchesQuizAnswer(JsonNode options, String answer) {
        for (JsonNode option : options) {
            if (option.isTextual() && answer.equals(option.asText())) {
                return true;
            }
            if (option.isObject()
                    && (answer.equals(option.path("id").asText())
                    || answer.equals(option.path("value").asText())
                    || answer.equals(option.path("text").asText()))) {
                return true;
            }
        }
        return false;
    }

    private void validateFlashcardBlock(LessonBlock block, List<ValidationError> errors) {
        if (block.getType() != LessonBlockType.FLASHCARD) {
            return;
        }
        if (!StringUtils.hasText(block.getFlashcardsJson())) {
            errors.add(new ValidationError("MSG-COURSE-011",
                    "Bộ Flashcard cần có ít nhất một thẻ hợp lệ.", "error"));
            return;
        }

        try {
            List<Map<String, String>> flashcards = objectMapper.readValue(block.getFlashcardsJson(),
                    new TypeReference<>() {
                    });
            if (flashcards.isEmpty()) {
                errors.add(new ValidationError("MSG-COURSE-011",
                        "Bộ Flashcard cần có ít nhất một thẻ hợp lệ.", "error"));
                return;
            }
            Set<String> fronts = new HashSet<>();
            for (Map<String, String> card : flashcards) {
                String front = card.get("front");
                String back = card.get("back");
                if (!StringUtils.hasText(front) || !StringUtils.hasText(back)) {
                    errors.add(new ValidationError("MSG-COURSE-011",
                            "Mỗi Flashcard cần có đầy đủ mặt trước và mặt sau.", "error"));
                    break;
                }
                if (!fronts.add(front.trim().toLowerCase())) {
                    errors.add(new ValidationError("MSG-COURSE-010",
                            "Phát hiện thẻ từ vựng bị trùng lặp. Vui lòng kiểm tra lại bộ Flashcard.", "error"));
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse flashcards JSON for block {}", block.getId());
            errors.add(new ValidationError("MSG-COURSE-011",
                    "Dữ liệu Flashcard không hợp lệ.", "error"));
        }
    }

    private void validateWritingBlock(LessonBlock block, List<ValidationError> errors) {
        if (block.getType() == LessonBlockType.WRITING && !StringUtils.hasText(block.getRubric())) {
            errors.add(new ValidationError("MSG-WRITE-005", "Bài writing cần chọn rubric đánh giá trước khi lưu.",
                    "error"));
        }
    }

    private void validateFinalTest(Course course, List<ValidationError> errors) {
        FinalTest finalTest = courseEditDraftService.hasEditDraft(course.getId())
                ? course.getFinalTest()
                : finalTestRepository.findByCourseId(course.getId()).orElse(null);
        if (finalTest == null) {
            errors.add(new ValidationError("MSG-FINAL-001",
                    "Vui lòng cấu hình bài kiểm tra cuối khóa trước khi gửi duyệt.", "error"));
            return;
        }

        if (finalTest.getTimeLimitMinutes() == null || finalTest.getTimeLimitMinutes() <= 0) {
            errors.add(new ValidationError("MSG-FINAL-002", "Thời gian làm bài kiểm tra cuối khóa phải lớn hơn 0 phút.",
                    "error"));
        }
        if (finalTest.getPassingScore() == null || finalTest.getPassingScore() < 0
                || finalTest.getPassingScore() > 100) {
            errors.add(new ValidationError("MSG-FINAL-003",
                    "Điểm đạt bài kiểm tra cuối khóa phải nằm trong khoảng 0 đến 100.", "error"));
        }
        if (finalTest.getMaxRetakes() == null || finalTest.getMaxRetakes() < 0) {
            errors.add(new ValidationError("MSG-FINAL-004", "Số lần làm lại bài kiểm tra cuối khóa không được âm.",
                    "error"));
        }
        if (!StringUtils.hasText(finalTest.getProficiencyLevel()) && finalTest.getJlptLevel() == null) {
            errors.add(new ValidationError("MSG-FINAL-005", "Vui lòng chọn cấp độ cho bài kiểm tra cuối khóa.",
                    "error"));
        }
        if (finalTest.getLearningLanguage() != null
                && course.getLearningLanguage() != null
                && finalTest.getLearningLanguage() != course.getLearningLanguage()) {
            errors.add(new ValidationError("MSG-FINAL-005",
                    "Ngôn ngữ của bài kiểm tra phải khớp với khóa học.", "error"));
        }
        if (!StringUtils.hasText(finalTest.getSkillFocus())) {
            errors.add(new ValidationError("MSG-FINAL-006",
                    "Vui lòng chọn kỹ năng trọng tâm cho bài kiểm tra cuối khóa.", "error"));
        }

        List<FinalTestQuestion> questions = finalTest.getQuestions();
        if (questions == null || questions.size() < 20) {
            errors.add(new ValidationError("MSG-FINAL-007", "Bài kiểm tra cuối khóa cần có tối thiểu 20 câu hỏi.",
                    "error"));
            return;
        }

        Set<String> uniqueQuestions = new HashSet<>();
        for (FinalTestQuestion question : questions) {
            validateFinalTestQuestion(question, uniqueQuestions, errors);
        }
    }

    private void validateFinalTestQuestion(FinalTestQuestion question, Set<String> uniqueQuestions,
            List<ValidationError> errors) {
        if (!StringUtils.hasText(question.getContent())) {
            errors.add(new ValidationError("MSG-FINAL-008", "Mỗi câu hỏi trong bài kiểm tra cuối khóa cần có nội dung.",
                    "error"));
            return;
        }

        String normalizedContent = question.getContent().trim().toLowerCase();
        if (!uniqueQuestions.add(normalizedContent)) {
            errors.add(new ValidationError("MSG-FINAL-009", "Phát hiện câu hỏi bài kiểm tra cuối khóa bị trùng lặp.",
                    "error"));
        }

        if (!StringUtils.hasText(question.getExplanation())) {
            errors.add(new ValidationError("MSG-FINAL-010",
                    "Mỗi câu hỏi trong bài kiểm tra cuối khóa cần có giải thích đáp án.", "error"));
        }

        List<FinalTestChoice> choices = question.getChoices();
        if (choices == null || choices.size() < 2) {
            errors.add(new ValidationError("MSG-FINAL-011",
                    "Mỗi câu hỏi trong bài kiểm tra cuối khóa cần có tối thiểu 2 lựa chọn.", "error"));
            return;
        }

        long correctChoices = choices.stream()
                .filter(choice -> Boolean.TRUE.equals(choice.getIsCorrect()))
                .count();
        if (correctChoices != 1) {
            errors.add(new ValidationError("MSG-FINAL-012",
                    "Mỗi câu hỏi trong bài kiểm tra cuối khóa phải có đúng 1 đáp án đúng.", "error"));
        }

        boolean hasBlankChoice = choices.stream()
                .anyMatch(choice -> !StringUtils.hasText(choice.getContent()));
        if (hasBlankChoice) {
            errors.add(new ValidationError("MSG-FINAL-013",
                    "Các lựa chọn trong bài kiểm tra cuối khóa không được để trống.", "error"));
        }
    }
}
