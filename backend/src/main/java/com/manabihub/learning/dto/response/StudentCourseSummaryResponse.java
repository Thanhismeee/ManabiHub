package com.manabihub.learning.dto.response;

import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class StudentCourseSummaryResponse {
    private UUID enrollmentId;
    private UUID courseId;
    private String courseTitle;
    private LearningLanguage learningLanguage;
    private ProficiencyFramework proficiencyFramework;
    private String frameworkVersion;
    private String proficiencyLevel;
    private String thumbnailUrl;
    private String teacherName;
    private EnrollmentStatus enrollmentStatus;
    private Instant enrolledAt;
    private Instant expiresAt;
    private long daysRemaining;
    private double progressPercentage;
}
