package com.manabihub.learning.service.impl;

import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.CourseModule;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.course.repository.CourseModuleRepository;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.repository.LessonBlockRepository;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.learning.dto.request.ReviewFlashcardRequest;
import com.manabihub.learning.dto.response.LessonProgressResponse;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.entity.LessonBlockProgress;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.enums.FlashcardStatus;
import com.manabihub.learning.enums.LessonProgressStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.learning.repository.FlashcardProgressRepository;
import com.manabihub.learning.repository.LessonBlockProgressRepository;
import com.manabihub.learning.service.LearningService;
import com.manabihub.writing.dto.request.WritingSubmissionRequest;
import com.manabihub.writing.dto.response.WritingSubmissionDetailResponse;
import com.manabihub.writing.repository.WritingSubmissionRepository;
import com.manabihub.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public class LearningServiceConcurrencyPostgresTest {

    private static final UUID DEMO_USER_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID DEMO_STUDENT_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID DEMO_COURSE_ID = UUID.fromString("f0000000-0000-0000-0000-000000000001");
    private static final UUID DEMO_ENROLLMENT_ID = UUID.fromString("f4000000-0000-0000-0000-000000000001");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("manabihub_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired private LearningService learningService;
    @Autowired private AppUserRepository userRepository;
    @Autowired private StudentProfileRepository studentProfileRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseModuleRepository courseModuleRepository;
    @Autowired private LessonBlockRepository lessonBlockRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private LessonBlockProgressRepository lessonBlockProgressRepository;
    @Autowired private FlashcardProgressRepository flashcardProgressRepository;
    @Autowired private WritingSubmissionRepository writingSubmissionRepository;

    @MockBean private CurrentUserService currentUserService;
    @MockBean private com.manabihub.ai.provider.AiWritingAssistanceProvider aiWritingAssistanceProvider;
    @Autowired private com.manabihub.ai.repository.AiUsageLogRepository aiUsageLogRepository;
    @Autowired private com.manabihub.writing.repository.AiWritingSuggestionRepository aiWritingSuggestionRepository;
    @MockBean private com.manabihub.ai.service.AiChatSettingsService aiChatSettingsService;

    private AppUser user;
    private Course course;
    private CourseModule module;
    private LessonBlock flashcardBlock;
    private LessonBlock writingBlock;
    private Enrollment enrollment;

    @BeforeEach
    void setUp() {
        user = userRepository.findById(DEMO_USER_ID)
                .orElseThrow(() -> new IllegalStateException("Demo user not found in Flyway seed"));
        course = courseRepository.findById(DEMO_COURSE_ID)
                .orElseThrow(() -> new IllegalStateException("Demo course not found in Flyway seed"));
        enrollment = enrollmentRepository.findById(DEMO_ENROLLMENT_ID)
                .orElseThrow(() -> new IllegalStateException("Demo enrollment not found in Flyway seed"));

        module = courseModuleRepository.saveAndFlush(
                CourseModule.builder()
                        .course(course)
                        .title("MHB-28 " + UUID.randomUUID())
                        .orderIndex(1000)
                        .blocks(new ArrayList<>())
                        .build()
        );

        flashcardBlock = lessonBlockRepository.saveAndFlush(
                LessonBlock.builder()
                        .type(LessonBlockType.FLASHCARD)
                        .title("Flashcards")
                        .module(module)
                        .orderIndex(1)
                        .flashcardsJson("[{\"front\":\"A\",\"back\":\"B\"}, {\"front\":\"C\",\"back\":\"D\"}]")
                        .build()
        );

        writingBlock = lessonBlockRepository.saveAndFlush(
                LessonBlock.builder()
                        .type(LessonBlockType.WRITING)
                        .title("Writing Assignment")
                        .module(module)
                        .orderIndex(2)
                        .writingPrompt("Test Prompt")
                        .build()
        );

        // The production learning flow is sequential.  This concurrency fixture
        // appends isolated blocks to the demo course, so mark any pre-existing
        // blocks as completed; otherwise the new sequential-unlock guard would
        // (correctly) reject the first operation in every test.
        markEarlierBlocksCompleted(enrollment, Set.of(flashcardBlock.getId(), writingBlock.getId()));

        when(currentUserService.getCurrentUserId()).thenReturn(DEMO_USER_ID);
        when(aiChatSettingsService.getSettings()).thenReturn(new com.manabihub.ai.service.AiChatSettingsService.AiChatSettings(
                true, true, true, new java.math.BigDecimal("0"), 100, 1000
        ));
    }

    @Test
    @DisplayName("Concurrent same-card reviews produce exactly 1 row and zero exceptions")
    void testConcurrentSameCard() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<LessonProgressResponse>> futures = new ArrayList<>();
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return learningService.reviewFlashcard(flashcardBlock.getId(),
                            new ReviewFlashcardRequest(0, FlashcardStatus.REMEMBERED));
                }));
            }

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "Workers did not become ready in time");
            startLatch.countDown();

            List<Throwable> errors = new ArrayList<>();
            for (Future<LessonProgressResponse> f : futures) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    errors.add(e.getCause() != null ? e.getCause() : e);
                }
            }
            assertEquals(0, errors.size(), "All requests must succeed, but got: " + errors);

            int count = flashcardProgressRepository.countByEnrollmentIdAndLessonBlockId(enrollment.getId(), flashcardBlock.getId());
            assertEquals(1, count, "Exactly 1 flashcard_progress row for card_index=0");

            var persisted = flashcardProgressRepository.findByEnrollmentIdAndLessonBlockIdAndCardIndex(
                    enrollment.getId(), flashcardBlock.getId(), 0).orElseThrow();
            assertEquals(FlashcardStatus.REMEMBERED, persisted.getStatus());

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Executor must terminate");
        } finally {
            startLatch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Concurrent card-0 and card-1 reviews produce 2 rows and COMPLETED")
    void testConcurrentDifferentCards() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<LessonProgressResponse>> futures = new ArrayList<>();
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            for (int i = 0; i < threads; i++) {
                int cardIndex = i % 2;
                FlashcardStatus status = cardIndex == 0 ? FlashcardStatus.REMEMBERED : FlashcardStatus.NEEDS_REVIEW;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return learningService.reviewFlashcard(flashcardBlock.getId(),
                            new ReviewFlashcardRequest(cardIndex, status));
                }));
            }

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "Workers did not become ready in time");
            startLatch.countDown();

            List<Throwable> errors = new ArrayList<>();
            for (Future<LessonProgressResponse> f : futures) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    errors.add(e.getCause() != null ? e.getCause() : e);
                }
            }
            assertEquals(0, errors.size(), "All requests must succeed, but got: " + errors);

            int count = flashcardProgressRepository.countByEnrollmentIdAndLessonBlockId(enrollment.getId(), flashcardBlock.getId());
            assertEquals(2, count, "Exactly 2 flashcard_progress rows (one per card)");

            LessonBlockProgress finalProgress = lessonBlockProgressRepository
                    .findByEnrollmentIdAndLessonBlockId(enrollment.getId(), flashcardBlock.getId())
                    .orElseThrow(() -> new AssertionError("LessonBlockProgress must exist"));
            assertEquals(LessonProgressStatus.COMPLETED, finalProgress.getStatus(),
                    "All cards reviewed => block COMPLETED");

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Executor must terminate");
        } finally {
            startLatch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Sequential persisted reload: upsert updates, count drives completion")
    void testSequentialPersistedReload() {
        LessonProgressResponse r1 = learningService.reviewFlashcard(flashcardBlock.getId(),
                new ReviewFlashcardRequest(0, FlashcardStatus.NEEDS_REVIEW));
        assertEquals(LessonProgressStatus.IN_PROGRESS, r1.status());

        LessonProgressResponse r2 = learningService.reviewFlashcard(flashcardBlock.getId(),
                new ReviewFlashcardRequest(0, FlashcardStatus.REMEMBERED));
        assertEquals(LessonProgressStatus.IN_PROGRESS, r2.status());

        int count = flashcardProgressRepository.countByEnrollmentIdAndLessonBlockId(enrollment.getId(), flashcardBlock.getId());
        assertEquals(1, count, "Still 1 row after upsert update");

        var persisted = flashcardProgressRepository.findByEnrollmentIdAndLessonBlockIdAndCardIndex(
                enrollment.getId(), flashcardBlock.getId(), 0).orElseThrow();
        assertEquals(FlashcardStatus.REMEMBERED, persisted.getStatus(), "Status must be updated to REMEMBERED");

        LessonProgressResponse r3 = learningService.reviewFlashcard(flashcardBlock.getId(),
                new ReviewFlashcardRequest(1, FlashcardStatus.REMEMBERED));
        assertEquals(LessonProgressStatus.COMPLETED, r3.status());
        assertNotNull(r3.completedAt());
    }

    @Test
    @DisplayName("Another student's reviews are isolated")
    void testAnotherStudentIsolation() {
        AppUser user2 = userRepository.saveAndFlush(
                AppUser.builder()
                        .email("m28-" + UUID.randomUUID() + "@example.com")
                        .fullName("Test 2")
                        .build()
        );
        StudentProfile student2 = studentProfileRepository.saveAndFlush(
                StudentProfile.builder()
                        .user(user2)
                        .displayName("Student 2")
                        .build()
        );
        Enrollment enrollment2 = enrollmentRepository.saveAndFlush(
                Enrollment.builder()
                        .student(student2)
                        .course(course)
                        .status(EnrollmentStatus.ACTIVE)
                        .build()
        );

        markEarlierBlocksCompleted(enrollment2, Set.of(flashcardBlock.getId(), writingBlock.getId()));

        when(currentUserService.getCurrentUserId()).thenReturn(user2.getId());
        learningService.reviewFlashcard(flashcardBlock.getId(), new ReviewFlashcardRequest(0, FlashcardStatus.REMEMBERED));

        int count2 = flashcardProgressRepository.countByEnrollmentIdAndLessonBlockId(enrollment2.getId(), flashcardBlock.getId());
        assertEquals(1, count2, "Student 2 must have 1 row");

        int count1 = flashcardProgressRepository.countByEnrollmentIdAndLessonBlockId(enrollment.getId(), flashcardBlock.getId());
        assertEquals(0, count1, "Student 1 must have 0 rows");
    }

    @Test
    @DisplayName("Concurrent writing submissions produce exactly 1 row and duplicate errors")
    void testConcurrentWritingSubmission() throws Exception {
        markBlockCompleted(enrollment, flashcardBlock);
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<com.manabihub.writing.dto.response.StudentWritingSubmissionResponse>> futures = new ArrayList<>();
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return learningService.submitWriting(writingBlock.getId(), new WritingSubmissionRequest("Concurrent test content"));
                }));
            }

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "Workers did not become ready in time");
            startLatch.countDown();

            int successCount = 0;
            int errorCount = 0;
            for (Future<com.manabihub.writing.dto.response.StudentWritingSubmissionResponse> f : futures) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                    successCount++;
                } catch (Exception e) {
                    errorCount++;
                    Throwable cause = e.getCause();
                    assertTrue(cause instanceof BusinessException, "Expected BusinessException, got " + cause);
                }
            }

            assertEquals(1, successCount, "Exactly 1 request must succeed");
            assertEquals(threads - 1, errorCount, "Other requests must fail");

            long count = writingSubmissionRepository.findByEnrollmentIdAndLessonBlockId(enrollment.getId(), writingBlock.getId()).stream().count();
            assertEquals(1, count, "Exactly 1 writing submission row should be present for this enrollment and block");

            com.manabihub.writing.entity.WritingSubmission winner = writingSubmissionRepository.findByEnrollmentIdAndLessonBlockId(enrollment.getId(), writingBlock.getId()).get();
            assertNotNull(winner.getSubmittedAt(), "submittedAt must not be null");
            assertEquals(writingBlock.getId(), winner.getLessonBlockId(), "lessonBlockId must match");
            assertNull(winner.getLegacyLessonId(), "legacyLessonId must be null");
            assertEquals(com.manabihub.writing.enums.WritingSubmissionStatus.SUBMITTED, winner.getStatus(), "Status must be SUBMITTED");

            com.manabihub.learning.entity.LessonBlockProgress progress = lessonBlockProgressRepository.findByEnrollmentIdAndLessonBlockId(enrollment.getId(), writingBlock.getId()).orElseThrow();
            assertEquals(com.manabihub.learning.enums.LessonProgressStatus.COMPLETED, progress.getStatus(), "Block progress must be COMPLETED");

            com.manabihub.learning.entity.Enrollment freshEnrollment = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
            assertEquals(com.manabihub.learning.enums.EnrollmentStatus.ACTIVE, freshEnrollment.getStatus(), "Enrollment must remain ACTIVE");

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Executor must terminate");
        } finally {
            startLatch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Provider failure durability on PostgreSQL")
    void testProviderFailureDurability() {
        markBlockCompleted(enrollment, flashcardBlock);
        learningService.submitWriting(writingBlock.getId(), new WritingSubmissionRequest("Test content"));
        com.manabihub.writing.entity.WritingSubmission submission = writingSubmissionRepository
                .findByEnrollmentIdAndLessonBlockId(enrollment.getId(), writingBlock.getId()).orElseThrow();

        when(aiWritingAssistanceProvider.generate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.manabihub.ai.provider.AiChatProviderException("HTTP 503 Provider Unavailable"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                learningService.requestAiWritingAssistance(writingBlock.getId(), submission.getId())
        );
        assertEquals(com.manabihub.common.constants.MessageCodes.MSG_AI_002, ex.getMessageCode());

        com.manabihub.writing.entity.WritingSubmission updatedSub = writingSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertEquals(com.manabihub.writing.enums.WritingSubmissionStatus.SUGGESTION_FAILED, updatedSub.getStatus());

        com.manabihub.writing.entity.AiWritingSuggestion suggestion = aiWritingSuggestionRepository.findFirstByWritingSubmission_IdOrderByCreatedAtDesc(submission.getId()).orElseThrow();
        assertEquals("FAILED", suggestion.getStatus());
        assertFalse(suggestion.isOfficial());
        assertTrue(suggestion.getGrammarSuggestions().isArray());
        assertTrue(suggestion.getVocabularySuggestions().isArray());
        assertTrue(suggestion.getStructureSuggestions().isArray());
        assertTrue(suggestion.getFailureReason().contains("Provider error"));

        com.manabihub.ai.entity.AiUsageLog log = aiUsageLogRepository.findAll().stream()
                .filter(l -> l.getLessonBlockId().equals(writingBlock.getId()))
                .findFirst().orElseThrow();
        assertEquals(com.manabihub.ai.enums.AiUsageRequestStatus.FAILED, log.getRequestStatus());
    }

    @Test
    @DisplayName("Concurrent AI requests for same submission only call provider once")
    void testConcurrentAiRequestsForSameSubmission() throws Exception {
        markBlockCompleted(enrollment, flashcardBlock);
        learningService.submitWriting(writingBlock.getId(), new WritingSubmissionRequest("Test content"));
        com.manabihub.writing.entity.WritingSubmission submission = writingSubmissionRepository
                .findByEnrollmentIdAndLessonBlockId(enrollment.getId(), writingBlock.getId()).orElseThrow();

        when(aiWritingAssistanceProvider.generate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    Thread.sleep(500); // simulate slow provider to force concurrency
                    return new com.manabihub.ai.provider.AiWritingAssistanceProvider.Result(
                            new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode(),
                            new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode(),
                            new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode(),
                            "Guidance", "provider", 10, 20
                    );
                });

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<com.manabihub.writing.dto.response.StudentWritingSubmissionResponse>> futures = new ArrayList<>();
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return learningService.requestAiWritingAssistance(writingBlock.getId(), submission.getId());
                }));
            }

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "Workers did not become ready in time");
            startLatch.countDown();

            for (Future<com.manabihub.writing.dto.response.StudentWritingSubmissionResponse> f : futures) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // It's expected that some return response idempotently and some might succeed.
                    // Wait, if it's already processing, it returns immediately with processing status.
                }
            }

            org.mockito.Mockito.verify(aiWritingAssistanceProvider, org.mockito.Mockito.times(1))
                    .generate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Executor must terminate");
        } finally {
            startLatch.countDown();
            executor.shutdownNow();
        }
    }

    private void markEarlierBlocksCompleted(Enrollment targetEnrollment, Set<UUID> excludedBlockIds) {
        List<CourseModule> modules = courseModuleRepository.findByCourse_IdOrderByOrderIndexAsc(course.getId());
        for (CourseModule courseModule : modules) {
            for (LessonBlock block : lessonBlockRepository.findByModule_IdOrderByOrderIndexAsc(courseModule.getId())) {
                if (excludedBlockIds.contains(block.getId())) {
                    continue;
                }
                if (lessonBlockProgressRepository
                        .findByEnrollmentIdAndLessonBlockId(targetEnrollment.getId(), block.getId())
                        .isEmpty()) {
                    lessonBlockProgressRepository.save(
                            LessonBlockProgress.builder()
                                    .enrollmentId(targetEnrollment.getId())
                                    .lessonBlockId(block.getId())
                                    .status(LessonProgressStatus.COMPLETED)
                                    .completedAt(java.time.Instant.now())
                                    .build()
                    );
                }
            }
        }
        lessonBlockProgressRepository.flush();
    }

    private void markBlockCompleted(Enrollment targetEnrollment, LessonBlock block) {
        lessonBlockProgressRepository.saveAndFlush(
                LessonBlockProgress.builder()
                        .enrollmentId(targetEnrollment.getId())
                        .lessonBlockId(block.getId())
                        .status(LessonProgressStatus.COMPLETED)
                        .completedAt(java.time.Instant.now())
                        .build()
        );
    }
}
