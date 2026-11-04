package com.manabihub.writing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WritingSubmissionRequest(
        @NotBlank(message = "Nội dung bài viết không được để trống")
        @Size(max = 10000, message = "Nội dung bài viết không được vượt quá 10,000 ký tự")
        String content
) {
}
