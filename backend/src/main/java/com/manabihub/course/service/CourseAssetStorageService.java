package com.manabihub.course.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.dto.response.CourseThumbnailUploadResponse;
import com.manabihub.course.entity.CourseThumbnailAsset;
import com.manabihub.course.repository.CourseThumbnailAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseAssetStorageService {

    private static final long MAX_THUMBNAIL_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_THUMBNAIL_TYPES = Set.of("image/jpeg", "image/png");

    private final CourseThumbnailAssetRepository courseThumbnailAssetRepository;

    @Value("${manabihub.course.thumbnail-public-path:/uploads/course-thumbnails}")
    private String publicPathPrefix;

    @Transactional
    public CourseThumbnailUploadResponse storeThumbnail(MultipartFile thumbnail) {
        validateThumbnail(thumbnail);

        String contentType = thumbnail.getContentType();
        String extension = "image/png".equals(contentType) ? "png" : "jpg";
        String fileName = "course-thumbnail-" + UUID.randomUUID() + "." + extension;

        try {
            courseThumbnailAssetRepository.save(CourseThumbnailAsset.builder()
                    .id(UUID.randomUUID())
                    .fileName(fileName)
                    .contentType(contentType)
                    .sizeBytes(thumbnail.getSize())
                    .content(thumbnail.getBytes())
                    .createdAt(Instant.now())
                    .build());
        } catch (IOException exception) {
            throw new BusinessException(
                    MessageCodes.MSG_COURSE_005,
                    "Could not store course thumbnail",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    exception
            );
        }

        String normalizedPublicPath = publicPathPrefix.endsWith("/")
                ? publicPathPrefix.substring(0, publicPathPrefix.length() - 1)
                : publicPathPrefix;
        return new CourseThumbnailUploadResponse(
                normalizedPublicPath + "/" + fileName,
                fileName,
                contentType,
                thumbnail.getSize()
        );
    }

    @Transactional(readOnly = true)
    public Optional<CourseThumbnailContent> loadThumbnail(String fileName) {
        if (fileName == null || !fileName.matches("course-thumbnail-[0-9a-fA-F-]{36}\\.(png|jpg)")) {
            return Optional.empty();
        }
        return courseThumbnailAssetRepository.findByFileName(fileName)
                .map(asset -> new CourseThumbnailContent(asset.getContentType(), asset.getContent()));
    }

    private void validateThumbnail(MultipartFile thumbnail) {
        if (thumbnail == null || thumbnail.isEmpty()) {
            throw new BusinessException(MessageCodes.MSG_COURSE_005, "Course thumbnail file is required");
        }

        if (thumbnail.getSize() > MAX_THUMBNAIL_SIZE_BYTES) {
            throw new BusinessException(MessageCodes.MSG_COURSE_005, "Course thumbnail must not exceed 5MB");
        }

        if (!ALLOWED_THUMBNAIL_TYPES.contains(thumbnail.getContentType())) {
            throw new BusinessException(MessageCodes.MSG_COURSE_005, "Course thumbnail must be PNG or JPG");
        }
    }

    public record CourseThumbnailContent(String contentType, byte[] content) {
    }
}
