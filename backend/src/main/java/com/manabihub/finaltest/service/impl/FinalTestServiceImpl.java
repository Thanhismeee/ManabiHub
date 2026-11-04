package com.manabihub.finaltest.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.course.domain.CourseLanguageProfile;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.revision.CourseEditDraftService;
import com.manabihub.finaltest.dto.request.FinalTestChoiceDto;
import com.manabihub.finaltest.dto.request.FinalTestQuestionDto;
import com.manabihub.finaltest.dto.request.UpdateFinalTestRequest;
import com.manabihub.finaltest.dto.response.FinalTestResponse;
import com.manabihub.finaltest.entity.FinalTest;
import com.manabihub.finaltest.entity.FinalTestChoice;
import com.manabihub.finaltest.entity.FinalTestQuestion;
import com.manabihub.finaltest.repository.FinalTestRepository;
import com.manabihub.finaltest.service.FinalTestService;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.domain.TeacherKycStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinalTestServiceImpl implements FinalTestService {

    private final FinalTestRepository finalTestRepository;
    private final CourseRepository courseRepository;
    private final CurrentUserService currentUserService;
    private final CourseEditDraftService courseEditDraftService;

    @Override
    @Transactional(readOnly = true)
    public FinalTestResponse getFinalTest(UUID courseId) {
        Course persistedCourse = validateAndGetCourse(courseId, currentUserService.getCurrentUserId());
        Course course = courseEditDraftService.resolveEditableCourse(persistedCourse);
        FinalTest finalTest = courseEditDraftService.hasEditDraft(courseId)
                ? course.getFinalTest()
                : finalTestRepository.findByCourseId(courseId).orElse(null);

        if (finalTest == null) {
            return null; // Return empty or 404, usually if it's draft, null is fine for GET.
        }

        return mapToResponse(finalTest);
    }

    @Override
    @Transactional
    public FinalTestResponse updateFinalTest(UUID courseId, UpdateFinalTestRequest request) {
        Course persistedCourse = validateAndGetCourse(courseId, currentUserService.getCurrentUserId());
        Course course = courseEditDraftService.resolveEditableCourse(persistedCourse);

        // Validation for Questions
        validateQuestions(request.getQuestions());

        boolean versionedEdit = courseEditDraftService.hasEditDraft(courseId);
        FinalTest finalTest = versionedEdit
                ? course.getFinalTest()
                : finalTestRepository.findByCourseId(courseId).orElse(null);
        if (finalTest == null) {
            finalTest = FinalTest.builder()
                    .id(versionedEdit ? UUID.randomUUID() : null)
                    .course(course)
                    .build();
        }

        CourseLanguageProfile languageProfile = CourseLanguageProfile.resolve(
                request.getLearningLanguage() == null ? course.getLearningLanguage() : request.getLearningLanguage(),
                request.getProficiencyFramework() == null ? course.getProficiencyFramework() : request.getProficiencyFramework(),
                request.getFrameworkVersion() == null ? course.getFrameworkVersion() : request.getFrameworkVersion(),
                request.getProficiencyLevel() == null ? course.getProficiencyLevel() : request.getProficiencyLevel(),
                request.getJlptLevel()
        );

        finalTest.setTimeLimitMinutes(request.getTimeLimitMinutes());
        finalTest.setPassingScore(request.getPassingScore());
        finalTest.setMaxRetakes(request.getMaxRetakes());
        finalTest.setJlptLevel(languageProfile.legacyJlptLevel());
        finalTest.setLearningLanguage(languageProfile.learningLanguage());
        finalTest.setProficiencyFramework(languageProfile.proficiencyFramework());
        finalTest.setFrameworkVersion(languageProfile.frameworkVersion());
        finalTest.setProficiencyLevel(languageProfile.proficiencyLevel());
        finalTest.setAssessmentFormat(normalizeAssessmentFormat(request.getAssessmentFormat()));
        finalTest.setSkillFocus(request.getSkillFocus());

        // Update Questions
        finalTest.getQuestions().clear();

        int qOrder = 0;
        for (FinalTestQuestionDto qDto : request.getQuestions()) {
            FinalTestQuestion question = FinalTestQuestion.builder()
                    .id(versionedEdit ? UUID.randomUUID() : null)
                    .finalTest(finalTest)
                    .content(qDto.getContent())
                    .explanation(qDto.getExplanation())
                    .orderIndex(qOrder++)
                    .build();

            int cOrder = 0;
            for (FinalTestChoiceDto cDto : qDto.getChoices()) {
                FinalTestChoice choice = FinalTestChoice.builder()
                        .id(versionedEdit ? UUID.randomUUID() : null)
                        .question(question)
                        .content(cDto.getContent())
                        .isCorrect(cDto.getIsCorrect())
                        .orderIndex(cOrder++)
                        .build();
                question.getChoices().add(choice);
            }
            finalTest.getQuestions().add(question);
        }

        if (versionedEdit) {
            course.setFinalTest(finalTest);
            courseEditDraftService.saveIfVersioned(course);
            return mapToResponse(finalTest);
        }

        FinalTest saved = finalTestRepository.save(finalTest);

        // Ensure course references final test
        course.setFinalTest(saved);
        courseRepository.save(course);

        return mapToResponse(saved);
    }

    private Course validateAndGetCourse(UUID courseId, UUID currentUserId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(MessageCodes.COURSE_NOT_FOUND, "Course not found"));

        TeacherProfile teacher = course.getTeacher();
        if (teacher == null || !teacher.getUser().getId().equals(currentUserId)) {
            throw new BusinessException(MessageCodes.MSG_ADM_002, "Not authorized");
        }

        if (teacher.getKycStatus() != TeacherKycStatus.PENDING
                && teacher.getKycStatus() != TeacherKycStatus.APPROVED) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_010,
                    "Complete identity and professional qualification submission before configuring the final test"
            );
        }

        if (course.getStatus() != CourseStatus.DRAFT && course.getStatus() != CourseStatus.REJECTED && course.getStatus() != CourseStatus.FORCED_DRAFT) {
            throw new BusinessException(MessageCodes.COURSE_NOT_EDITABLE, "Course is not in draft mode");
        }

        return course;
    }

    private void validateQuestions(List<FinalTestQuestionDto> questions) {
        if (questions == null || questions.size() < 20) {
            throw new BusinessException(MessageCodes.MSG_FTEST_001, "Final Test requires minimum 20 questions");
        }

        Set<String> contentSet = new HashSet<>();
        for (FinalTestQuestionDto q : questions) {
            if (q.getContent() == null || q.getContent().trim().isEmpty() ||
                q.getExplanation() == null || q.getExplanation().trim().isEmpty()) {
                throw new BusinessException(MessageCodes.MSG_FTEST_002, "Question missing content or explanation");
            }

            if (!contentSet.add(q.getContent().trim().toLowerCase())) {
                throw new BusinessException(MessageCodes.MSG_FTEST_004, "Duplicate question detected");
            }

            if (q.getChoices() == null || q.getChoices().isEmpty()) {
                throw new BusinessException(MessageCodes.MSG_FTEST_002, "Question missing choices");
            }

            long correctCount = q.getChoices().stream()
                    .filter(c -> Boolean.TRUE.equals(c.getIsCorrect()))
                    .count();

            if (correctCount != 1) {
                throw new BusinessException(MessageCodes.MSG_FTEST_002, "Question must have exactly one correct choice");
            }
        }
    }

    private FinalTestResponse mapToResponse(FinalTest entity) {
        FinalTestResponse response = new FinalTestResponse();
        response.setId(entity.getId());
        response.setCourseId(entity.getCourse().getId());
        response.setTimeLimitMinutes(entity.getTimeLimitMinutes());
        response.setPassingScore(entity.getPassingScore());
        response.setMaxRetakes(entity.getMaxRetakes());
        response.setJlptLevel(entity.getJlptLevel());
        response.setLearningLanguage(entity.getLearningLanguage());
        response.setProficiencyFramework(entity.getProficiencyFramework());
        response.setFrameworkVersion(entity.getFrameworkVersion());
        response.setProficiencyLevel(entity.getProficiencyLevel());
        response.setAssessmentFormat(entity.getAssessmentFormat());
        response.setSkillFocus(entity.getSkillFocus());

        List<FinalTestQuestionDto> qDtos = new ArrayList<>();
        if (entity.getQuestions() != null) {
            for (FinalTestQuestion q : entity.getQuestions()) {
                FinalTestQuestionDto qDto = new FinalTestQuestionDto();
                qDto.setId(q.getId());
                qDto.setContent(q.getContent());
                qDto.setExplanation(q.getExplanation());

                List<FinalTestChoiceDto> cDtos = new ArrayList<>();
                if (q.getChoices() != null) {
                    for (FinalTestChoice c : q.getChoices()) {
                        FinalTestChoiceDto cDto = new FinalTestChoiceDto();
                        cDto.setId(c.getId());
                        cDto.setContent(c.getContent());
                        cDto.setIsCorrect(c.getIsCorrect());
                        cDtos.add(cDto);
                    }
                }
                qDto.setChoices(cDtos);
                qDtos.add(qDto);
            }
        }
        response.setQuestions(qDtos);
        return response;
    }

    private String normalizeAssessmentFormat(String value) {
        if (value == null || value.isBlank()) {
            return "COURSE";
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("COURSE", "PBT", "IBT").contains(normalized)) {
            throw new BusinessException(
                    MessageCodes.MSG_FTEST_003,
                    "Bài kiểm tra cuối khóa hiện hỗ trợ trắc nghiệm COURSE/PBT/IBT. Giao bài nói trong lớp học để giáo viên đánh giá."
            );
        }
        return normalized;
    }
}
