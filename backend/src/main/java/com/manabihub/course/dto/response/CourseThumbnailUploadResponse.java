package com.manabihub.course.dto.response;

public record CourseThumbnailUploadResponse(
        String publicUrl,
        String fileName,
        String contentType,
        long size
) {
}
