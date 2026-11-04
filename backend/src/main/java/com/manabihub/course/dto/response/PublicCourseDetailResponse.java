package com.manabihub.course.dto.response;

import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PublicCourseDetailResponse {
    private UUID id;
    private String title;
    private String slug;
    private String description;
    private String introduction;
    private JlptLevel jlptLevel;
    private LearningLanguage learningLanguage;
    private ProficiencyFramework proficiencyFramework;
    private String frameworkVersion;
    private String proficiencyLevel;
    private String category;
    private String thumbnailUrl;
    private String outcomes;
    private BigDecimal price;
    private String currency;
    private String prerequisites;
    private String targetStudents;
    private Instant publishedAt;
    private Boolean aiSupported;
    private Integer accessDurationDays;
    private Instant accessExpiresAt;

    // Teacher Info
    private TeacherDto teacher;

    // Enrollment is resolved from the authenticated public session when present.
    private Boolean isEnrolled;
    private Boolean hasExpiredEnrollment;
    private Boolean isTeacherOwner;

    // Aggregation
    private Integer totalDurationMinutes;
    private Integer totalLessons;
    private BigDecimal averageRating;
    private Long reviewCount;

    private List<PublicModuleResponse> modules;

    @Data
    @Builder
    public static class TeacherDto {
        private UUID id;
        private String name;
        private String avatarUrl;
        private String bio;
        private boolean verified;
    }
}
