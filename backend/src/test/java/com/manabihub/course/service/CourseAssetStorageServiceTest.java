package com.manabihub.course.service;

import com.manabihub.course.entity.CourseThumbnailAsset;
import com.manabihub.course.repository.CourseThumbnailAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseAssetStorageServiceTest {

    @Mock
    private CourseThumbnailAssetRepository repository;

    private CourseAssetStorageService service;

    @BeforeEach
    void setUp() {
        service = new CourseAssetStorageService(repository);
        ReflectionTestUtils.setField(service, "publicPathPrefix", "/uploads/course-thumbnails");
    }

    @Test
    void storesThumbnailInPersistentRepositoryAndReturnsStableUrl() {
        byte[] image = new byte[]{1, 2, 3, 4};
        MockMultipartFile upload = new MockMultipartFile(
                "thumbnail", "cover.png", "image/png", image);
        when(repository.save(any(CourseThumbnailAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.storeThumbnail(upload);

        ArgumentCaptor<CourseThumbnailAsset> asset = ArgumentCaptor.forClass(CourseThumbnailAsset.class);
        verify(repository).save(asset.capture());
        assertThat(asset.getValue().getContent()).isEqualTo(image);
        assertThat(asset.getValue().getContentType()).isEqualTo("image/png");
        assertThat(asset.getValue().getSizeBytes()).isEqualTo(image.length);
        assertThat(response.publicUrl()).startsWith("/uploads/course-thumbnails/course-thumbnail-");
        assertThat(response.fileName()).endsWith(".png");
    }

    @Test
    void loadsPersistedThumbnailByGeneratedFileName() {
        String fileName = "course-thumbnail-" + UUID.randomUUID() + ".jpg";
        byte[] image = new byte[]{5, 6, 7};
        when(repository.findByFileName(fileName)).thenReturn(Optional.of(CourseThumbnailAsset.builder()
                .id(UUID.randomUUID())
                .fileName(fileName)
                .contentType("image/jpeg")
                .sizeBytes(image.length)
                .content(image)
                .createdAt(Instant.now())
                .build()));

        var result = service.loadThumbnail(fileName);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().contentType()).isEqualTo("image/jpeg");
        assertThat(result.orElseThrow().content()).isEqualTo(image);
    }

    @Test
    void rejectsUnexpectedFileNameWithoutRepositoryLookup() {
        assertThat(service.loadThumbnail("../application.yml")).isEmpty();
    }
}
