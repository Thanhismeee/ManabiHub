package com.manabihub.course.dto.response;

import java.util.List;

public record CourseApprovalCriterionResponse(
        String code,
        String title,
        String description,
        boolean passed,
        List<String> reasons
) {
}
