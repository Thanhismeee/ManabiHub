package com.manabihub.kyc.dto;

public record TeacherQualificationEvidenceDownload(
        byte[] content,
        String fileName,
        String mimeType
) {
}
