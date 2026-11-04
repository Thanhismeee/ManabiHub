package com.manabihub.learning.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

public record LearningCertificateResponse(
        UUID id,
        UUID enrollmentId,
        UUID courseId,
        String certificateNumber,
        String studentName,
        String courseTitle,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant issuedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant completedAt
) {
}
