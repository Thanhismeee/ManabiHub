package com.manabihub.cohort.dto;

import com.manabihub.cohort.domain.CohortMemberStatus;
import com.manabihub.cohort.domain.CohortStatus;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import com.manabihub.learning.enums.EnrollmentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CohortResponse(
        UUID id,
        UUID courseId,
        String courseTitle,
        LearningLanguage learningLanguage,
        ProficiencyFramework proficiencyFramework,
        String proficiencyLevel,
        String name,
        String code,
        String organizationName,
        String branchName,
        LocalDate startDate,
        LocalDate endDate,
        int maxStudents,
        CohortStatus status,
        long activeStudentCount,
        List<MemberResponse> members,
        Instant createdAt,
        Instant updatedAt
) {
    public record MemberResponse(
            UUID id,
            UUID studentId,
            String fullName,
            String email,
            CohortMemberStatus status,
            EnrollmentStatus enrollmentStatus,
            Instant joinedAt,
            Instant accessExpiresAt
    ) {
    }
}
