package com.manabihub.review.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeacherCourseReviewReplyRequest(
        @NotBlank(message = "Nội dung phản hồi là bắt buộc")
        @Size(min = 2, max = 2000, message = "Nội dung phản hồi phải có từ 2 đến 2.000 ký tự")
        String replyText
) {
}
