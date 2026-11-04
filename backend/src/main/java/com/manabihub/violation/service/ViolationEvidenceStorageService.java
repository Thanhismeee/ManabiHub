package com.manabihub.violation.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

@Service
public class ViolationEvidenceStorageService {

    public static final String STORAGE_PREFIX = "private:violation-evidence:";
    public static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;

    private static final byte[] PNG_SIGNATURE =
            new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF_SIGNATURE = new byte[]{0x25, 0x50, 0x44, 0x46};

    private final Path storageRoot;

    public ViolationEvidenceStorageService(
            @Value("${manabihub.violation.evidence-storage-root:storage/violation-evidence}")
            String storageRoot
    ) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public StoredEvidence store(UUID reportId, MultipartFile file) {
        DetectedFile detected = validate(file);
        String storageKey = reportId + "/" + UUID.randomUUID() + "." + detected.extension();
        Path target = resolveStorageKey(storageKey);

        try {
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BusinessException(
                    MessageCodes.COMMON_INTERNAL_ERROR,
                    "Không thể lưu tệp bằng chứng.",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    exception
            );
        }

        return new StoredEvidence(
                storageKey,
                safeOriginalName(file.getOriginalFilename()),
                detected.contentType(),
                detected.evidenceType()
        );
    }

    public Resource load(String storageKey) {
        try {
            Resource resource = new UrlResource(resolveStorageKey(storageKey).toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw evidenceNotFound();
            }
            return resource;
        } catch (IOException exception) {
            throw evidenceNotFound();
        }
    }

    public void deleteQuietly(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolveStorageKey(storageKey));
        } catch (IOException ignored) {
            // Orphan cleanup can be retried by an operational maintenance job.
        }
    }

    public String toStoredReference(String storageKey) {
        return STORAGE_PREFIX + storageKey;
    }

    public String parseStorageKey(String storedReference) {
        if (storedReference == null || !storedReference.startsWith(STORAGE_PREFIX)) {
            throw evidenceNotFound();
        }
        String storageKey = storedReference.substring(STORAGE_PREFIX.length());
        resolveStorageKey(storageKey);
        return storageKey;
    }

    private DetectedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidEvidence("Tệp bằng chứng không được để trống.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw invalidEvidence("Mỗi tệp bằng chứng không được vượt quá 5 MB.");
        }

        byte[] header = new byte[8];
        int read;
        try (InputStream inputStream = file.getInputStream()) {
            read = inputStream.read(header);
        } catch (IOException exception) {
            throw invalidEvidence("Không thể đọc tệp bằng chứng.");
        }

        if (read >= PNG_SIGNATURE.length
                && Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(header, PNG_SIGNATURE.length))) {
            return new DetectedFile("png", "image/png", "IMAGE");
        }
        if (read >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return new DetectedFile("jpg", "image/jpeg", "IMAGE");
        }
        if (read >= PDF_SIGNATURE.length
                && Arrays.equals(PDF_SIGNATURE, Arrays.copyOf(header, PDF_SIGNATURE.length))) {
            return new DetectedFile("pdf", "application/pdf", "DOCUMENT");
        }
        throw invalidEvidence("Bằng chứng chỉ chấp nhận tệp PDF, PNG hoặc JPEG.");
    }

    private Path resolveStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw evidenceNotFound();
        }
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw invalidEvidence("Đường dẫn lưu trữ bằng chứng không hợp lệ.");
        }
        return resolved;
    }

    private String safeOriginalName(String originalName) {
        String name = originalName == null || originalName.isBlank()
                ? "bang-chung"
                : Path.of(originalName).getFileName().toString();
        name = name.replaceAll("[\\p{Cntrl}]", "_");
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private BusinessException invalidEvidence(String message) {
        return new BusinessException(MessageCodes.VALIDATION_FAILED, message, HttpStatus.BAD_REQUEST);
    }

    private BusinessException evidenceNotFound() {
        return new BusinessException(
                MessageCodes.COMMON_NOT_FOUND,
                "Không tìm thấy tệp bằng chứng.",
                HttpStatus.NOT_FOUND
        );
    }

    public record StoredEvidence(
            String storageKey,
            String originalName,
            String contentType,
            String evidenceType
    ) {
    }

    private record DetectedFile(String extension, String contentType, String evidenceType) {
    }
}
