package com.manabihub.payout.service;

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
public class PayoutProofStorageService {

    private static final long MAX_PROOF_SIZE = 5L * 1024L * 1024L;
    private static final byte[] PNG_SIGNATURE =
            new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF_SIGNATURE = new byte[]{0x25, 0x50, 0x44, 0x46};

    private final Path storageRoot;

    public PayoutProofStorageService(
            @Value("${manabihub.payout.proof-storage-root:storage/payout-proofs}") String storageRoot
    ) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public StoredProof store(UUID withdrawalRequestId, MultipartFile proof) {
        DetectedFile detected = validate(proof);
        String storageKey = withdrawalRequestId + "/"
                + UUID.randomUUID() + "." + detected.extension();
        Path target = resolveStorageKey(storageKey);

        try {
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = proof.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_PROOF_INVALID,
                    "Could not store payout transfer proof.",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    exception
            );
        }

        return new StoredProof(
                storageKey,
                safeOriginalName(proof.getOriginalFilename()),
                detected.contentType(),
                proof.getSize()
        );
    }

    public Resource load(String storageKey) {
        Path path = resolveStorageKey(storageKey);
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw proofNotFound();
            }
            return resource;
        } catch (IOException exception) {
            throw proofNotFound();
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

    private DetectedFile validate(MultipartFile proof) {
        if (proof == null || proof.isEmpty()) {
            throw invalidProof("Transfer proof is required.");
        }
        if (proof.getSize() > MAX_PROOF_SIZE) {
            throw invalidProof("Transfer proof must not exceed 5 MB.");
        }

        byte[] header = new byte[8];
        int read;
        try (InputStream inputStream = proof.getInputStream()) {
            read = inputStream.read(header);
        } catch (IOException exception) {
            throw invalidProof("Transfer proof could not be read.");
        }

        if (read >= PNG_SIGNATURE.length
                && Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(header, PNG_SIGNATURE.length))) {
            return new DetectedFile("png", "image/png");
        }
        if (read >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return new DetectedFile("jpg", "image/jpeg");
        }
        if (read >= PDF_SIGNATURE.length
                && Arrays.equals(PDF_SIGNATURE, Arrays.copyOf(header, PDF_SIGNATURE.length))) {
            return new DetectedFile("pdf", "application/pdf");
        }
        throw invalidProof("Transfer proof must be a PDF, PNG, or JPEG file.");
    }

    private Path resolveStorageKey(String storageKey) {
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw invalidProof("Invalid payout proof storage path.");
        }
        return resolved;
    }

    private String safeOriginalName(String originalName) {
        String name = originalName == null || originalName.isBlank()
                ? "transfer-proof"
                : Path.of(originalName).getFileName().toString();
        name = name.replaceAll("[\\p{Cntrl}]", "_");
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private BusinessException invalidProof(String message) {
        return new BusinessException(
                MessageCodes.PAYOUT_PROOF_INVALID,
                message,
                HttpStatus.BAD_REQUEST
        );
    }

    private BusinessException proofNotFound() {
        return new BusinessException(
                MessageCodes.PAYOUT_PROOF_NOT_FOUND,
                "Payout transfer proof was not found.",
                HttpStatus.NOT_FOUND
        );
    }

    public record StoredProof(
            String storageKey,
            String originalName,
            String contentType,
            long size
    ) {
    }

    private record DetectedFile(String extension, String contentType) {
    }
}
