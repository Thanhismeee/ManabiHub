package com.manabihub.course.dto.response;

import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight course summary DTO for catalog listing.
 * Intentionally omits modules, lesson blocks, and detailed descriptions
 * to keep the list response payload small.
 */
@Data
@Builder
public class PublicCourseSummaryResponse {
    private UUID id;
    private String title;
    private String slug;
    private String thumbnailUrl;
    private JlptLevel jlptLevel;
    private LearningLanguage learningLanguage;
    private ProficiencyFramework proficiencyFramework;
    private String frameworkVersion;
    private String proficiencyLevel;
    private String category;
    private BigDecimal price;
    private String currency;
    private UUID teacherId;
    private String teacherName;
    private String teacherAvatarUrl;
    private Integer totalLessons;
    private Instant publishedAt;
    private BigDecimal averageRating;
    private Long reviewCount;
    private Long enrollmentCount;
}
