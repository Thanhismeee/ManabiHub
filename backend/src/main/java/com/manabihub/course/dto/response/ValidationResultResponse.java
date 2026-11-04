package com.manabihub.course.dto.response;

import java.util.List;

public record ValidationResultResponse(
        boolean isValid,
        List<ValidationError> errors
) {
}
