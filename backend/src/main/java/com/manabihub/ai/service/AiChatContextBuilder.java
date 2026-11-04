package com.manabihub.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.ai.domain.AiChatContext;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.LessonBlock;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class AiChatContextBuilder {

    private static final int MAX_CONTEXT_LENGTH = 12_000;
    
    private final ObjectMapper objectMapper;
    
    public AiChatContextBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiChatContext build(Course course, LessonBlock lessonBlock) {
        String blockContent = buildLessonContent(lessonBlock);

        return new AiChatContext(
                course.getId(),
                lessonBlock.getId(),
                course.getLearningLanguage(),
                course.getProficiencyFramework(),
                course.getFrameworkVersion(),
                course.getProficiencyLevel(),
                course.getTitle(),
                truncate(course.getDescription(), 2_000),
                truncate(course.getOutcomes(), 2_000),
                lessonBlock.getTitle(),
                truncate(blockContent, MAX_CONTEXT_LENGTH)
        );
    }

    private String buildLessonContent(LessonBlock lessonBlock) {
        StringBuilder content = new StringBuilder();
        appendSection(content, "Lesson text", lessonBlock.getContent());
        
        String quizItems = parseQuizItems(lessonBlock.getQuizItemsJson());
        if (StringUtils.hasText(quizItems)) {
            appendSection(content, "Quiz questions", quizItems);
        } else {
            appendSection(content, "Quiz question", lessonBlock.getQuizQuestion());
            appendSection(content, "Quiz options", lessonBlock.getQuizOptionsJson());
        }
        
        String flashcards = parseFlashcards(lessonBlock.getFlashcardsJson());
        appendSection(content, "Flashcards", flashcards);
        
        appendSection(content, "Writing prompt", lessonBlock.getWritingPrompt());
        appendSection(content, "Writing rubric", lessonBlock.getRubric());

        if (content.isEmpty()) {
            return "No text content is available for this lesson block.";
        }
        return content.toString();
    }
    
    private String parseQuizItems(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            List<QuizItem> items = objectMapper.readValue(json, new TypeReference<>() {});
            if (items == null || items.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                QuizItem item = items.get(i);
                sb.append("Câu ").append(i + 1).append(": ").append(item.question()).append("\n");
                if (item.options() != null && !item.options().isEmpty()) {
                    for (String option : item.options()) {
                        sb.append("  - ").append(option).append("\n");
                    }
                }
            }
            return sb.toString().trim();
        } catch (JsonProcessingException e) {
            return null;
        }
    }
    
    private String parseFlashcards(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            List<FlashcardItem> items = objectMapper.readValue(json, new TypeReference<>() {});
            if (items == null || items.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (FlashcardItem item : items) {
                sb.append("Term: ").append(item.front()).append(" | Definition: ").append(item.back()).append("\n");
            }
            return sb.toString().trim();
        } catch (JsonProcessingException e) {
            return null; // fallback to null to omit section
        }
    }

    private void appendSection(StringBuilder target, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (!target.isEmpty()) {
            target.append("\n\n");
        }
        target.append(label).append(":\n").append(value.trim());
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
    
    private record QuizItem(String question, List<String> options, String answer) {}
    
    private record FlashcardItem(String front, String back) {}
}
