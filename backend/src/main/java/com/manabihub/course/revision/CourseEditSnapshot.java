package com.manabihub.course.revision;

import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.CourseLearningGoal;
import com.manabihub.course.entity.CourseModule;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.course.enums.ProficiencyFramework;
import com.manabihub.finaltest.entity.FinalTest;
import com.manabihub.finaltest.entity.FinalTestChoice;
import com.manabihub.finaltest.entity.FinalTestQuestion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Serializable shadow aggregate used while editing a previously published
 * course. It deliberately contains only course-owned, editable data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseEditSnapshot {

    private String title;
    private String slug;
    private String description;
    private String introduction;
    private JlptLevel jlptLevel;
    private LearningLanguage learningLanguage;
    private ProficiencyFramework proficiencyFramework;
    private String frameworkVersion;
    private String proficiencyLevel;
    private String category;
    private String thumbnailUrl;
    private String outcomes;
    private BigDecimal price;
    private String currency;
    private String prerequisites;
    private String targetStudents;
    private Integer accessDurationDays;
    private Instant accessExpiresAt;
    @Builder.Default
    private List<GoalSnapshot> learningGoals = new ArrayList<>();
    @Builder.Default
    private List<ModuleSnapshot> modules = new ArrayList<>();
    private FinalTestSnapshot finalTest;

    public static CourseEditSnapshot fromCourse(Course course) {
        return CourseEditSnapshot.builder()
                .title(course.getTitle())
                .slug(course.getSlug())
                .description(course.getDescription())
                .introduction(course.getIntroduction())
                .jlptLevel(course.getJlptLevel())
                .learningLanguage(course.getLearningLanguage())
                .proficiencyFramework(course.getProficiencyFramework())
                .frameworkVersion(course.getFrameworkVersion())
                .proficiencyLevel(course.getProficiencyLevel())
                .category(course.getCategory())
                .thumbnailUrl(course.getThumbnailUrl())
                .outcomes(course.getOutcomes())
                .price(course.getPrice())
                .currency(course.getCurrency())
                .prerequisites(course.getPrerequisites())
                .targetStudents(course.getTargetStudents())
                .accessDurationDays(course.getAccessDurationDays())
                .accessExpiresAt(course.getAccessExpiresAt())
                .learningGoals(course.getLearningGoals().stream()
                        .sorted(Comparator.comparingInt(CourseLearningGoal::getOrderIndex))
                        .map(GoalSnapshot::fromEntity)
                        .toList())
                .modules(course.getModules().stream()
                        .sorted(Comparator.comparingInt(CourseModule::getOrderIndex))
                        .map(ModuleSnapshot::fromEntity)
                        .toList())
                .finalTest(course.getFinalTest() == null ? null : FinalTestSnapshot.fromEntity(course.getFinalTest()))
                .build();
    }

    /** Creates a detached Course-shaped view so existing DTO mappers and validators can be reused safely. */
    public Course toEditableCourse(Course persistedCourse) {
        LearningLanguage resolvedLanguage = learningLanguage != null
                ? learningLanguage
                : persistedCourse.getLearningLanguage();
        ProficiencyFramework resolvedFramework = proficiencyFramework != null
                ? proficiencyFramework
                : persistedCourse.getProficiencyFramework();
        String resolvedFrameworkVersion = frameworkVersion != null
                ? frameworkVersion
                : persistedCourse.getFrameworkVersion();
        String resolvedProficiencyLevel = proficiencyLevel != null
                ? proficiencyLevel
                : (jlptLevel != null ? jlptLevel.name() : persistedCourse.getProficiencyLevel());
        Course editable = Course.builder()
                .id(persistedCourse.getId())
                .teacher(persistedCourse.getTeacher())
                .title(title)
                .slug(slug)
                .description(description)
                .introduction(introduction)
                .jlptLevel(jlptLevel)
                .learningLanguage(resolvedLanguage)
                .proficiencyFramework(resolvedFramework)
                .frameworkVersion(resolvedFrameworkVersion)
                .proficiencyLevel(resolvedProficiencyLevel)
                .category(category)
                .thumbnailUrl(thumbnailUrl)
                .outcomes(outcomes)
                .price(price)
                .currency(currency)
                .prerequisites(prerequisites)
                .targetStudents(targetStudents)
                .accessDurationDays(accessDurationDays)
                .accessExpiresAt(accessExpiresAt)
                .status(persistedCourse.getStatus())
                .aiSupported(persistedCourse.isAiSupported())
                .submittedAt(persistedCourse.getSubmittedAt())
                .approvedBy(persistedCourse.getApprovedBy())
                .approvedAt(persistedCourse.getApprovedAt())
                .rejectionReason(persistedCourse.getRejectionReason())
                .publishedAt(persistedCourse.getPublishedAt())
                .createdAt(persistedCourse.getCreatedAt())
                .updatedAt(persistedCourse.getUpdatedAt())
                .build();

        learningGoals.stream()
                .sorted(Comparator.comparingInt(GoalSnapshot::getOrderIndex))
                .forEach(goal -> editable.getLearningGoals().add(goal.toEntity(editable)));
        modules.stream()
                .sorted(Comparator.comparingInt(ModuleSnapshot::getOrderIndex))
                .forEach(module -> editable.addModule(module.toEntity(editable)));
        if (finalTest != null) {
            editable.setFinalTest(finalTest.toEntity(editable));
        }
        return editable;
    }

    public void applyMetadataTo(Course course) {
        course.setTitle(title);
        course.setSlug(slug);
        course.setDescription(description);
        course.setIntroduction(introduction);
        course.setJlptLevel(jlptLevel);
        if (learningLanguage != null) {
            course.setLearningLanguage(learningLanguage);
        }
        if (proficiencyFramework != null) {
            course.setProficiencyFramework(proficiencyFramework);
        }
        if (frameworkVersion != null) {
            course.setFrameworkVersion(frameworkVersion);
        }
        if (proficiencyLevel != null) {
            course.setProficiencyLevel(proficiencyLevel);
        } else if (jlptLevel != null) {
            course.setProficiencyLevel(jlptLevel.name());
        }
        course.setCategory(category);
        course.setThumbnailUrl(thumbnailUrl);
        course.setOutcomes(outcomes);
        course.setPrice(price);
        course.setCurrency(currency);
        course.setPrerequisites(prerequisites);
        course.setTargetStudents(targetStudents);
        course.setAccessDurationDays(accessDurationDays);
        course.setAccessExpiresAt(accessExpiresAt);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoalSnapshot {
        private UUID id;
        private String goalText;
        private int orderIndex;

        static GoalSnapshot fromEntity(CourseLearningGoal goal) {
            return new GoalSnapshot(goal.getId(), goal.getGoalText(), goal.getOrderIndex());
        }

        CourseLearningGoal toEntity(Course course) {
            return CourseLearningGoal.builder()
                    .id(id)
                    .course(course)
                    .goalText(goalText)
                    .orderIndex(orderIndex)
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModuleSnapshot {
        private UUID id;
        private String title;
        private String description;
        private int orderIndex;
        @Builder.Default
        private List<BlockSnapshot> blocks = new ArrayList<>();

        static ModuleSnapshot fromEntity(CourseModule module) {
            return ModuleSnapshot.builder()
                    .id(module.getId())
                    .title(module.getTitle())
                    .description(module.getDescription())
                    .orderIndex(module.getOrderIndex())
                    .blocks(module.getBlocks().stream()
                            .sorted(Comparator.comparingInt(LessonBlock::getOrderIndex))
                            .map(BlockSnapshot::fromEntity)
                            .toList())
                    .build();
        }

        CourseModule toEntity(Course course) {
            CourseModule module = CourseModule.builder()
                    .id(id)
                    .course(course)
                    .title(title)
                    .description(description)
                    .orderIndex(orderIndex)
                    .build();
            blocks.stream()
                    .sorted(Comparator.comparingInt(BlockSnapshot::getOrderIndex))
                    .forEach(block -> module.addBlock(block.toEntity(module)));
            return module;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlockSnapshot {
        private UUID id;
        private LessonBlockType type;
        private String title;
        private String content;
        private String videoUrl;
        private Integer durationMinutes;
        private String quizQuestion;
        private String quizOptionsJson;
        private String quizAnswer;
        private String quizItemsJson;
        private String flashcardsJson;
        private String writingPrompt;
        private String rubric;
        private int orderIndex;
        private boolean moderationHidden;
        private Instant moderationHiddenAt;

        static BlockSnapshot fromEntity(LessonBlock block) {
            return BlockSnapshot.builder()
                    .id(block.getId())
                    .type(block.getType())
                    .title(block.getTitle())
                    .content(block.getContent())
                    .videoUrl(block.getVideoUrl())
                    .durationMinutes(block.getDurationMinutes())
                    .quizQuestion(block.getQuizQuestion())
                    .quizOptionsJson(block.getQuizOptionsJson())
                    .quizAnswer(block.getQuizAnswer())
                    .quizItemsJson(block.getQuizItemsJson())
                    .flashcardsJson(block.getFlashcardsJson())
                    .writingPrompt(block.getWritingPrompt())
                    .rubric(block.getRubric())
                    .orderIndex(block.getOrderIndex())
                    .moderationHidden(block.isModerationHidden())
                    .moderationHiddenAt(block.getModerationHiddenAt())
                    .build();
        }

        LessonBlock toEntity(CourseModule module) {
            return LessonBlock.builder()
                    .id(id)
                    .module(module)
                    .type(type)
                    .title(title)
                    .content(content)
                    .videoUrl(videoUrl)
                    .durationMinutes(durationMinutes)
                    .quizQuestion(quizQuestion)
                    .quizOptionsJson(quizOptionsJson)
                    .quizAnswer(quizAnswer)
                    .quizItemsJson(quizItemsJson)
                    .flashcardsJson(flashcardsJson)
                    .writingPrompt(writingPrompt)
                    .rubric(rubric)
                    .orderIndex(orderIndex)
                    .moderationHidden(moderationHidden)
                    .moderationHiddenAt(moderationHiddenAt)
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinalTestSnapshot {
        private UUID id;
        private Integer timeLimitMinutes;
        private Integer passingScore;
        private Integer maxRetakes;
        private JlptLevel jlptLevel;
        private LearningLanguage learningLanguage;
        private ProficiencyFramework proficiencyFramework;
        private String frameworkVersion;
        private String proficiencyLevel;
        private String assessmentFormat;
        private String skillFocus;
        @Builder.Default
        private List<QuestionSnapshot> questions = new ArrayList<>();

        static FinalTestSnapshot fromEntity(FinalTest finalTest) {
            return FinalTestSnapshot.builder()
                    .id(finalTest.getId())
                    .timeLimitMinutes(finalTest.getTimeLimitMinutes())
                    .passingScore(finalTest.getPassingScore())
                    .maxRetakes(finalTest.getMaxRetakes())
                    .jlptLevel(finalTest.getJlptLevel())
                    .learningLanguage(finalTest.getLearningLanguage())
                    .proficiencyFramework(finalTest.getProficiencyFramework())
                    .frameworkVersion(finalTest.getFrameworkVersion())
                    .proficiencyLevel(finalTest.getProficiencyLevel())
                    .assessmentFormat(finalTest.getAssessmentFormat())
                    .skillFocus(finalTest.getSkillFocus())
                    .questions(finalTest.getQuestions().stream()
                            .sorted(Comparator.comparingInt(FinalTestQuestion::getOrderIndex))
                            .map(QuestionSnapshot::fromEntity)
                            .toList())
                    .build();
        }

        FinalTest toEntity(Course course) {
            LearningLanguage resolvedLanguage = learningLanguage != null
                    ? learningLanguage
                    : course.getLearningLanguage();
            ProficiencyFramework resolvedFramework = proficiencyFramework != null
                    ? proficiencyFramework
                    : course.getProficiencyFramework();
            String resolvedFrameworkVersion = frameworkVersion != null
                    ? frameworkVersion
                    : course.getFrameworkVersion();
            String resolvedProficiencyLevel = proficiencyLevel != null
                    ? proficiencyLevel
                    : (jlptLevel != null ? jlptLevel.name() : course.getProficiencyLevel());
            FinalTest test = FinalTest.builder()
                    .id(id)
                    .course(course)
                    .timeLimitMinutes(timeLimitMinutes)
                    .passingScore(passingScore)
                    .maxRetakes(maxRetakes)
                    .jlptLevel(jlptLevel)
                    .learningLanguage(resolvedLanguage)
                    .proficiencyFramework(resolvedFramework)
                    .frameworkVersion(resolvedFrameworkVersion)
                    .proficiencyLevel(resolvedProficiencyLevel)
                    .assessmentFormat(assessmentFormat == null ? "COURSE" : assessmentFormat)
                    .skillFocus(skillFocus)
                    .build();
            questions.stream()
                    .sorted(Comparator.comparingInt(QuestionSnapshot::getOrderIndex))
                    .forEach(question -> test.getQuestions().add(question.toEntity(test)));
            return test;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionSnapshot {
        private UUID id;
        private String content;
        private String explanation;
        private int orderIndex;
        @Builder.Default
        private List<ChoiceSnapshot> choices = new ArrayList<>();

        static QuestionSnapshot fromEntity(FinalTestQuestion question) {
            return QuestionSnapshot.builder()
                    .id(question.getId())
                    .content(question.getContent())
                    .explanation(question.getExplanation())
                    .orderIndex(question.getOrderIndex())
                    .choices(question.getChoices().stream()
                            .sorted(Comparator.comparingInt(FinalTestChoice::getOrderIndex))
                            .map(ChoiceSnapshot::fromEntity)
                            .toList())
                    .build();
        }

        FinalTestQuestion toEntity(FinalTest test) {
            FinalTestQuestion question = FinalTestQuestion.builder()
                    .id(id)
                    .finalTest(test)
                    .content(content)
                    .explanation(explanation)
                    .orderIndex(orderIndex)
                    .build();
            choices.stream()
                    .sorted(Comparator.comparingInt(ChoiceSnapshot::getOrderIndex))
                    .forEach(choice -> question.getChoices().add(choice.toEntity(question)));
            return question;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChoiceSnapshot {
        private UUID id;
        private String content;
        private Boolean correct;
        private int orderIndex;

        static ChoiceSnapshot fromEntity(FinalTestChoice choice) {
            return new ChoiceSnapshot(choice.getId(), choice.getContent(), choice.getIsCorrect(), choice.getOrderIndex());
        }

        FinalTestChoice toEntity(FinalTestQuestion question) {
            return FinalTestChoice.builder()
                    .id(id)
                    .question(question)
                    .content(content)
                    .isCorrect(correct)
                    .orderIndex(orderIndex)
                    .build();
        }
    }
}
