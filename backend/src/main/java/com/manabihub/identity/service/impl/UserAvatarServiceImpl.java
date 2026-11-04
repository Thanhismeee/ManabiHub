package com.manabihub.identity.service.impl;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.identity.service.UserAvatarService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class UserAvatarServiceImpl implements UserAvatarService {

    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final Path storageRoot;
    private final String publicPathPrefix;

    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;

    public UserAvatarServiceImpl(
            AppUserRepository appUserRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService,
            @Value("${manabihub.user.avatar-storage-root:storage/user-avatars}") String storageRoot,
            @Value("${manabihub.user.avatar-public-path:/uploads/user-avatars}") String publicPathPrefix
    ) {
        this.appUserRepository = appUserRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.publicPathPrefix = publicPathPrefix.endsWith("/")
                ? publicPathPrefix.substring(0, publicPathPrefix.length() - 1)
                : publicPathPrefix;
    }

    private String detectExtensionAndValidateMagicBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 4) {
                throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST, "Invalid image file format", HttpStatus.BAD_REQUEST);
            }

            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if (read >= 8 && header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E && header[3] == (byte) 0x47
                    && header[4] == (byte) 0x0D && header[5] == (byte) 0x0A && header[6] == (byte) 0x1A && header[7] == (byte) 0x0A) {
                return "png";
            }

            // JPEG: FF D8 FF
            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                return "jpeg";
            }

            // WebP: RIFF ... WEBP
            if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                return "webp";
            }

            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST, "Invalid image file format", HttpStatus.BAD_REQUEST);
        } catch (IOException e) {
            log.error("Failed to read file signature", e);
            throw new BusinessException(MessageCodes.COMMON_INTERNAL_ERROR, "Failed to process file", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public String uploadAvatar(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST, "File is empty", HttpStatus.BAD_REQUEST);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST, "File size exceeds 2MB", HttpStatus.BAD_REQUEST);
        }

        String contentType = file.getContentType();
        String extension = detectExtensionAndValidateMagicBytes(file);

        if (!("image/jpeg".equals(contentType) && "jpeg".equals(extension)) &&
            !("image/png".equals(contentType) && "png".equals(extension)) &&
            !("image/webp".equals(contentType) && "webp".equals(extension))) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST, "MIME type does not match file content", HttpStatus.BAD_REQUEST);
        }

        UUID userId = currentUserService.getCurrentUserId();
        AppUser user = appUserRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(MessageCodes.COMMON_NOT_FOUND, "User not found", HttpStatus.NOT_FOUND));

        String newFilename = UUID.randomUUID().toString() + "." + extension;
        Path targetPath = storageRoot.resolve(newFilename).normalize();

        if (!targetPath.startsWith(storageRoot)) {
            throw new BusinessException(MessageCodes.COMMON_BAD_REQUEST, "Invalid file path", HttpStatus.BAD_REQUEST);
        }

        try {
            Files.createDirectories(storageRoot);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to store avatar file", e);
            throw new BusinessException(MessageCodes.COMMON_INTERNAL_ERROR, "Failed to store avatar file", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String stableUrl = publicPathPrefix + "/" + newFilename;
        String oldAvatarUrl = user.getAvatarUrl();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        Files.deleteIfExists(targetPath);
                        log.debug("Rolled back avatar file: {}", targetPath);
                    } catch (IOException e) {
                        log.error("Failed to delete new avatar file on rollback: {}", targetPath, e);
                    }
                } else if (status == STATUS_COMMITTED) {
                    if (StringUtils.hasText(oldAvatarUrl)
                            && oldAvatarUrl.startsWith(publicPathPrefix + "/")) {
                        String oldFilename = oldAvatarUrl.substring(publicPathPrefix.length() + 1);
                        try {
                            Path filenamePath = Path.of(oldFilename);
                            boolean isSingleFilename = StringUtils.hasText(oldFilename)
                                    && !filenamePath.isAbsolute()
                                    && filenamePath.getNameCount() == 1
                                    && !".".equals(oldFilename)
                                    && !"..".equals(oldFilename);
                            if (isSingleFilename) {
                                Path oldPath = storageRoot.resolve(filenamePath).normalize();
                                Files.deleteIfExists(oldPath);
                                log.debug("Deleted old avatar file: {}", oldPath);
                            }
                        } catch (IOException | InvalidPathException e) {
                            log.warn("Failed to delete old avatar file after commit", e);
                        }
                    }
                }
            }
        });

        user.setAvatarUrl(stableUrl);
        appUserRepository.save(user);

        // Audit log
        Map<String, Object> after = Map.of(
                "contentType", contentType != null ? contentType : "unknown",
                "sizeBytes", file.getSize(),
                "storageType", "LOCAL"
        );
        auditLogService.logUserAction(
                userId,
                null,
                "UPDATE_AVATAR",
                "AppUser",
                userId,
                Map.of(),
                after,
                Map.of()
        );

        return stableUrl;
    }
}
