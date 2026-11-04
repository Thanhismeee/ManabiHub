package com.manabihub.course.repository;

import com.manabihub.course.entity.CourseThumbnailAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourseThumbnailAssetRepository extends JpaRepository<CourseThumbnailAsset, UUID> {

    Optional<CourseThumbnailAsset> findByFileName(String fileName);
}
