package com.manabihub.writing.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TeacherWritingFeedbackResponse(
        UUID id,
        BigDecimal score,
        String comment,
        JsonNode rubricResult,
        boolean official,
        Instant createdAt,
        Instant updatedAt
) {
}
