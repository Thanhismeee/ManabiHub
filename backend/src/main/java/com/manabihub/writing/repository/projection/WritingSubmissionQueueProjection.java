package com.manabihub.writing.repository.projection;

import java.time.Instant;
import java.util.UUID;

public interface WritingSubmissionQueueProjection {
    UUID getId();

    UUID getCourseId();

    String getCourseTitle();

    UUID getLessonId();

    String getLessonTitle();

    String getStudentName();

    String getStudentEmail();

    String getStatus();

    Instant getSubmittedAt();

    boolean getHasAiSuggestion();

    boolean getHasTeacherFeedback();

    java.math.BigDecimal getScore();
}
