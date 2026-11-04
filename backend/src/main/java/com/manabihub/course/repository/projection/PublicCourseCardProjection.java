package com.manabihub.course.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Scalar course-card fields loaded in one batch for a ranked page. */
public interface PublicCourseCardProjection {

    UUID getCourseId();

    String getTitle();

    String getSlug();

    String getThumbnailUrl();

    String getJlptLevel();

    String getLearningLanguage();

    String getProficiencyFramework();

    String getFrameworkVersion();

    String getProficiencyLevel();

    String getCategory();

    BigDecimal getPrice();

    String getCurrency();

    UUID getTeacherId();

    String getTeacherName();

    String getTeacherAvatarUrl();

    Instant getPublishedAt();
}
