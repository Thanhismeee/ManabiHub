package com.manabihub.learning.dto.response;

import com.manabihub.learning.enums.LessonProgressStatus;

import java.time.Instant;
import java.util.UUID;

public record LessonProgressResponse(
        UUID lessonBlockId,
        UUID enrollmentId,
        LessonProgressStatus status,
        Integer lastVideoPositionSeconds,
        Instant completedAt,
        Instant updatedAt,
        Integer watchedVideoSeconds
) {
    public LessonProgressResponse(
            UUID lessonBlockId,
            UUID enrollmentId,
            LessonProgressStatus status,
            Integer lastVideoPositionSeconds,
            Instant completedAt,
            Instant updatedAt
    ) {
        this(lessonBlockId, enrollmentId, status, lastVideoPositionSeconds, completedAt, updatedAt, null);
    }
}
