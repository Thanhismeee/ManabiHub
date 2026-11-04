package com.manabihub.course.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.course.dto.response.ValidationError;
import com.manabihub.course.dto.response.ValidationResultResponse;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.revision.CourseEditDraftService;
import com.manabihub.finaltest.repository.FinalTestRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.domain.AppUser;
import com.manabihub.kyc.domain.TeacherProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CourseValidationServiceImplTest {

    private static final String THUMBNAIL_URL_ERROR_CODE = "MSG-COURSE-021";

    @Mock
    private CourseRepository courseRepository;

    private ObjectMapper objectMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private FinalTestRepository finalTestRepository;

    @Mock
    private CourseEditDraftService courseEditDraftService;

    private CourseValidationServiceImpl courseValidationService;
    private UUID courseId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        courseValidationService = new CourseValidationServiceImpl(
                courseRepository,
                objectMapper,
                currentUserService,
                finalTestRepository,
                courseEditDraftService);
        org.mockito.Mockito.lenient()
                .when(courseEditDraftService.resolveEditableCourse(org.mockito.ArgumentMatchers.any(Course.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        courseId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @Order(1)
    @DisplayName("UTCID01 (N) - thumbnail is an uploaded asset path -> accepted")
    void validateCourse_WhenThumbnailUsesUploadedAssetPath_ShouldAcceptThumbnailUrl() {
        ValidationResultResponse result = validate(
                "/uploads/course-thumbnails/course-thumbnail-1234.png");

        assertFalse(hasThumbnailUrlError(result));
    }

    @Test
    @Order(2)
    @DisplayName("UTCID02 (N) - thumbnail is an external https URL -> accepted")
    void validateCourse_WhenThumbnailUsesExternalHttpsUrl_ShouldAcceptThumbnailUrl() {
        ValidationResultResponse result = validate(
                "https://cdn.example.com/course-thumbnail.png");

        assertFalse(hasThumbnailUrlError(result));
    }

    @Test
    @Order(5)
    @DisplayName("UTCID05 (A) - thumbnail is an unknown relative path -> rejected")
    void validateCourse_WhenThumbnailUsesUnknownRelativePath_ShouldRejectThumbnailUrl() {
        ValidationResultResponse result = validate("/images/course-thumbnail.png");

        assertTrue(hasThumbnailUrlError(result));
    }

    @Test
    @Order(3)
    @DisplayName("UTCID03 (N) - quiz options as a string array, answer matches -> valid")
    void validateQuizBlock_WhenOptionsAreStringArray_ShouldAcceptMatchingAnswer() {
        List<ValidationError> errors = validateQuizOptions(
                """
                ["こちらの会議室をご利用になる際は、受付で必要事項をご記入ください", "利用できません"]
                """,
                "こちらの会議室をご利用になる際は、受付で必要事項をご記入ください"
        );

        assertTrue(errors.isEmpty());
    }

    @Test
    @Order(4)
    @DisplayName("UTCID04 (N) - quiz options in the legacy object form -> valid")
    void validateQuizBlock_WhenOptionsUseLegacyObjects_ShouldAcceptMatchingAnswer() {
        List<ValidationError> errors = validateQuizOptions(
                """
                [{"id":"answer-a","text":"Đáp án A"},{"id":"answer-b","text":"Đáp án B"}]
                """,
                "answer-b"
        );

        assertTrue(errors.isEmpty());
    }

    @Test
    @Order(6)
    @DisplayName("UTCID06 (A) - quiz options JSON is malformed -> validation error")
    void validateQuizBlock_WhenOptionsJsonIsInvalid_ShouldReturnValidationError() {
        List<ValidationError> errors = validateQuizOptions("[invalid-json", "answer");

        assertEquals(1, errors.size());
        assertEquals("MSG-COURSE-013", errors.getFirst().code());
    }

    @Test
    @Order(7)
    @DisplayName("UTCID07 (A) - quiz options missing -> validation error")
    void validateQuizBlock_WhenOptionsAreMissing_ShouldReturnValidationError() {
        List<ValidationError> errors = validateQuizOptions(null, "answer");

        assertEquals(1, errors.size());
        assertEquals("MSG-COURSE-013", errors.getFirst().code());
    }

    @Test
    @Order(8)
    @DisplayName("UTCID08 (A) - course has no module -> rejected")
    void validateHierarchy_WhenCourseHasNoModules_ShouldRejectCourse() {
        Course course = Course.builder()
                .id(courseId)
                .modules(List.of())
                .build();
        List<ValidationError> errors = new ArrayList<>();

        ReflectionTestUtils.invokeMethod(
                courseValidationService,
                "validateHierarchy",
                course,
                errors
        );

        assertTrue(errors.stream().anyMatch(error -> "MSG-COURSE-015".equals(error.code())));
        assertTrue(errors.stream().anyMatch(error -> "MSG-COURSE-016".equals(error.code())));
    }

    @Test
    @Order(9)
    @DisplayName("UTCID09 (A) - flashcard JSON is malformed -> validation error")
    void validateFlashcardBlock_WhenJsonIsInvalid_ShouldReturnValidationError() {
        LessonBlock block = LessonBlock.builder()
                .id(UUID.randomUUID())
                .type(LessonBlockType.FLASHCARD)
                .flashcardsJson("[invalid-json")
                .build();
        List<ValidationError> errors = new ArrayList<>();

        ReflectionTestUtils.invokeMethod(
                courseValidationService,
                "validateFlashcardBlock",
                block,
                errors
        );

        assertEquals(1, errors.size());
        assertEquals("MSG-COURSE-011", errors.getFirst().code());
    }

    private ValidationResultResponse validate(String thumbnailUrl) {
        AppUser user = new AppUser();
        user.setId(userId);

        TeacherProfile teacher = new TeacherProfile();
        teacher.setId(UUID.randomUUID());
        teacher.setUser(user);

        Course course = Course.builder()
                .id(courseId)
                .teacher(teacher)
                .title("JLPT N5 Foundation")
                .introduction("Introductory Japanese course for new learners.")
                .jlptLevel(JlptLevel.N5)
                .category("GRAMMAR")
                .thumbnailUrl(thumbnailUrl)
                .outcomes("Understand basic N5 grammar and vocabulary.")
                .price(BigDecimal.valueOf(250_000))
                .prerequisites("No prerequisites")
                .targetStudents("Students starting Japanese from zero")
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(finalTestRepository.findByCourseId(courseId)).thenReturn(Optional.empty());

        return courseValidationService.validateCourse(courseId);
    }

    private boolean hasThumbnailUrlError(ValidationResultResponse result) {
        return result.errors().stream()
                .anyMatch(error -> THUMBNAIL_URL_ERROR_CODE.equals(error.code()));
    }

    @SuppressWarnings("unchecked")
    private List<ValidationError> validateQuizOptions(String optionsJson, String answer) {
        LessonBlock block = LessonBlock.builder()
                .id(UUID.randomUUID())
                .type(LessonBlockType.QUIZ)
                .quizOptionsJson(optionsJson)
                .quizAnswer(answer)
                .build();
        List<ValidationError> errors = new ArrayList<>();

        ReflectionTestUtils.invokeMethod(
                courseValidationService,
                "validateQuizBlock",
                block,
                errors
        );
        return errors;
    }
}
