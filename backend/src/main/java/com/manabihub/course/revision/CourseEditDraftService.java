package com.manabihub.course.revision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.CourseEditDraft;
import com.manabihub.course.entity.CourseLearningGoal;
import com.manabihub.course.entity.CourseModule;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.repository.CourseEditDraftRepository;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.finaltest.entity.FinalTest;
import com.manabihub.finaltest.entity.FinalTestChoice;
import com.manabihub.finaltest.entity.FinalTestQuestion;
import com.manabihub.finaltest.repository.FinalTestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseEditDraftService {

    private final CourseEditDraftRepository editDraftRepository;
    private final CourseRepository courseRepository;
    private final FinalTestRepository finalTestRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void beginEditingPublishedCourse(Course course) {
        if (editDraftRepository.existsById(course.getId())) {
            return;
        }
        Instant now = Instant.now();
        editDraftRepository.save(CourseEditDraft.builder()
                .courseId(course.getId())
                .snapshotJson(write(CourseEditSnapshot.fromCourse(course)))
                .basePublishedAt(course.getPublishedAt())
                .updatedAt(now)
                .build());
    }

    @Transactional(readOnly = true)
    public boolean hasEditDraft(UUID courseId) {
        return editDraftRepository.existsById(courseId);
    }

    @Transactional(readOnly = true)
    public Course resolveEditableCourse(Course persistedCourse) {
        return editDraftRepository.findById(persistedCourse.getId())
                .map(draft -> read(draft.getSnapshotJson()).toEditableCourse(persistedCourse))
                .orElse(persistedCourse);
    }

    /**
     * Persists a detached editable view when this is a revision of an already
     * published course. Returns false for ordinary, never-published drafts.
     */
    @Transactional
    public boolean saveIfVersioned(Course editableCourse) {
        CourseEditDraft draft = editDraftRepository.findById(editableCourse.getId()).orElse(null);
        if (draft == null) {
            return false;
        }
        draft.setSnapshotJson(write(CourseEditSnapshot.fromCourse(editableCourse)));
        draft.setUpdatedAt(Instant.now());
        editDraftRepository.save(draft);
        return true;
    }

    @Transactional(readOnly = true)
    public Instant resolveLastModifiedAt(Course persistedCourse) {
        return editDraftRepository.findById(persistedCourse.getId())
                .map(CourseEditDraft::getUpdatedAt)
                .orElseGet(() -> persistedCourse.getUpdatedAt() != null
                        ? persistedCourse.getUpdatedAt()
                        : persistedCourse.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public CourseEditSnapshot getSnapshot(UUID courseId) {
        return editDraftRepository.findById(courseId)
                .map(draft -> read(draft.getSnapshotJson()))
                .orElse(null);
    }

    /** Applies the approved shadow aggregate to the live course in one transaction. */
    @Transactional
    public boolean applyApprovedDraft(Course course) {
        CourseEditDraft draft = editDraftRepository.findById(course.getId()).orElse(null);
        if (draft == null) {
            return false;
        }

        CourseEditSnapshot snapshot = read(draft.getSnapshotJson());
        snapshot.applyMetadataTo(course);

        Map<UUID, CourseModule> existingModules = new HashMap<>();
        for (CourseModule module : course.getModules()) {
            existingModules.put(module.getId(), module);
        }

        // Free unique order indexes before reconciling the approved ordering.
        int negativeModuleOrder = -1;
        for (CourseModule module : course.getModules()) {
            module.setOrderIndex(negativeModuleOrder--);
            int negativeBlockOrder = -1;
            for (LessonBlock block : module.getBlocks()) {
                block.setOrderIndex(negativeBlockOrder--);
            }
        }
        course.getLearningGoals().clear();
        courseRepository.saveAndFlush(course);

        int goalOrder = 1;
        for (CourseEditSnapshot.GoalSnapshot goal : snapshot.getLearningGoals()) {
            course.addLearningGoal(goal.getGoalText(), goalOrder++);
        }

        Set<UUID> retainedModuleIds = new HashSet<>();
        List<CourseEditSnapshot.ModuleSnapshot> approvedModules = snapshot.getModules().stream()
                .sorted(Comparator.comparingInt(CourseEditSnapshot.ModuleSnapshot::getOrderIndex))
                .toList();

        for (int moduleIndex = 0; moduleIndex < approvedModules.size(); moduleIndex++) {
            CourseEditSnapshot.ModuleSnapshot approvedModule = approvedModules.get(moduleIndex);
            CourseModule module = existingModules.get(approvedModule.getId());
            if (module == null) {
                module = CourseModule.builder().course(course).blocks(new ArrayList<>()).build();
                course.addModule(module);
            } else {
                retainedModuleIds.add(module.getId());
            }
            module.setTitle(approvedModule.getTitle());
            module.setDescription(approvedModule.getDescription());
            module.setOrderIndex(moduleIndex + 1);
            reconcileBlocks(module, approvedModule.getBlocks());
        }

        course.getModules().removeIf(module -> module.getId() != null && !retainedModuleIds.contains(module.getId()));
        applyFinalTest(course, snapshot.getFinalTest());
        courseRepository.saveAndFlush(course);
        editDraftRepository.delete(draft);
        editDraftRepository.flush();
        return true;
    }

    @Transactional
    public void discard(UUID courseId) {
        editDraftRepository.deleteById(courseId);
    }

    private void reconcileBlocks(CourseModule module, List<CourseEditSnapshot.BlockSnapshot> approvedBlocks) {
        Map<UUID, LessonBlock> existingBlocks = new HashMap<>();
        for (LessonBlock block : module.getBlocks()) {
            existingBlocks.put(block.getId(), block);
        }

        Set<UUID> retainedBlockIds = new HashSet<>();
        List<CourseEditSnapshot.BlockSnapshot> sorted = approvedBlocks.stream()
                .sorted(Comparator.comparingInt(CourseEditSnapshot.BlockSnapshot::getOrderIndex))
                .toList();
        for (int index = 0; index < sorted.size(); index++) {
            CourseEditSnapshot.BlockSnapshot approved = sorted.get(index);
            LessonBlock block = existingBlocks.get(approved.getId());
            if (block == null) {
                block = LessonBlock.builder().module(module).build();
                module.addBlock(block);
            } else {
                retainedBlockIds.add(block.getId());
            }
            copyBlock(approved, block, index + 1);
        }
        module.getBlocks().removeIf(block -> block.getId() != null && !retainedBlockIds.contains(block.getId()));
    }

    private void copyBlock(CourseEditSnapshot.BlockSnapshot source, LessonBlock target, int orderIndex) {
        target.setType(source.getType());
        target.setTitle(source.getTitle());
        target.setContent(source.getContent());
        target.setVideoUrl(source.getVideoUrl());
        target.setDurationMinutes(source.getDurationMinutes());
        target.setQuizQuestion(source.getQuizQuestion());
        target.setQuizOptionsJson(source.getQuizOptionsJson());
        target.setQuizAnswer(source.getQuizAnswer());
        target.setQuizItemsJson(source.getQuizItemsJson());
        target.setFlashcardsJson(source.getFlashcardsJson());
        target.setWritingPrompt(source.getWritingPrompt());
        target.setRubric(source.getRubric());
        target.setOrderIndex(orderIndex);
        target.setModerationHidden(source.isModerationHidden());
        target.setModerationHiddenAt(source.getModerationHiddenAt());
    }

    private void applyFinalTest(Course course, CourseEditSnapshot.FinalTestSnapshot approved) {
        FinalTest current = finalTestRepository.findByCourseId(course.getId()).orElse(null);
        if (approved == null) {
            if (current != null) {
                course.setFinalTest(null);
                finalTestRepository.delete(current);
                finalTestRepository.flush();
            }
            return;
        }

        FinalTest target = current == null ? FinalTest.builder().course(course).build() : current;
        target.setTimeLimitMinutes(approved.getTimeLimitMinutes());
        target.setPassingScore(approved.getPassingScore());
        target.setMaxRetakes(approved.getMaxRetakes());
        target.setJlptLevel(approved.getJlptLevel());
        target.setSkillFocus(approved.getSkillFocus());
        target.getQuestions().clear();
        if (current != null) {
            finalTestRepository.saveAndFlush(target);
        }

        List<CourseEditSnapshot.QuestionSnapshot> questions = approved.getQuestions().stream()
                .sorted(Comparator.comparingInt(CourseEditSnapshot.QuestionSnapshot::getOrderIndex))
                .toList();
        for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
            CourseEditSnapshot.QuestionSnapshot approvedQuestion = questions.get(questionIndex);
            FinalTestQuestion question = FinalTestQuestion.builder()
                    .finalTest(target)
                    .content(approvedQuestion.getContent())
                    .explanation(approvedQuestion.getExplanation())
                    .orderIndex(questionIndex)
                    .build();
            List<CourseEditSnapshot.ChoiceSnapshot> choices = approvedQuestion.getChoices().stream()
                    .sorted(Comparator.comparingInt(CourseEditSnapshot.ChoiceSnapshot::getOrderIndex))
                    .toList();
            for (int choiceIndex = 0; choiceIndex < choices.size(); choiceIndex++) {
                CourseEditSnapshot.ChoiceSnapshot approvedChoice = choices.get(choiceIndex);
                question.getChoices().add(FinalTestChoice.builder()
                        .question(question)
                        .content(approvedChoice.getContent())
                        .isCorrect(approvedChoice.getCorrect())
                        .orderIndex(choiceIndex)
                        .build());
            }
            target.getQuestions().add(question);
        }
        FinalTest saved = finalTestRepository.save(target);
        course.setFinalTest(saved);
    }

    private CourseEditSnapshot read(String json) {
        try {
            return objectMapper.readValue(json, CourseEditSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    MessageCodes.COMMON_INTERNAL_ERROR,
                    "Bản nháp chỉnh sửa khóa học bị lỗi dữ liệu",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private String write(CourseEditSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    MessageCodes.COMMON_INTERNAL_ERROR,
                    "Không thể lưu bản nháp chỉnh sửa khóa học",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
