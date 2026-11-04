package com.manabihub.kyc.dto.response;

public record KycDocumentDownload(
        byte[] content,
        String fileName,
        String mimeType
) {
}
