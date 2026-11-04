package com.manabihub.course.dto.response;

public record ValidationError(
        String code,
        String message,
        String severity
) {
}
