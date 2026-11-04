package com.manabihub.violation.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViolationEvidenceStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndLoadsEvidenceUsingDetectedSignature() throws Exception {
        ViolationEvidenceStorageService service = new ViolationEvidenceStorageService(tempDir.toString());
        byte[] png = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3
        };
        MockMultipartFile file = new MockMultipartFile(
                "evidence", "screen.png", "image/png", png
        );

        ViolationEvidenceStorageService.StoredEvidence stored =
                service.store(UUID.randomUUID(), file);
        Resource resource = service.load(stored.storageKey());

        assertEquals("screen.png", stored.originalName());
        assertEquals("image/png", stored.contentType());
        assertEquals("IMAGE", stored.evidenceType());
        assertTrue(resource.exists());
        assertEquals(png.length, resource.contentLength());
    }

    @Test
    void rejectsFileWhoseContentDoesNotMatchAnAllowedSignature() {
        ViolationEvidenceStorageService service = new ViolationEvidenceStorageService(tempDir.toString());
        MockMultipartFile forged = new MockMultipartFile(
                "evidence", "forged.pdf", "application/pdf", "not-a-pdf".getBytes()
        );

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.store(UUID.randomUUID(), forged)
        );

        assertEquals(MessageCodes.VALIDATION_FAILED, error.getMessageCode());
    }

    @Test
    void rejectsPathTraversalWhenLoadingEvidence() {
        ViolationEvidenceStorageService service = new ViolationEvidenceStorageService(tempDir.toString());

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.load("../../application-local.yml")
        );

        assertEquals(MessageCodes.VALIDATION_FAILED, error.getMessageCode());
    }
}
