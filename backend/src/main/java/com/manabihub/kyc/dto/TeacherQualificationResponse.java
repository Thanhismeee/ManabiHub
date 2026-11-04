package com.manabihub.kyc.dto;

import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import com.manabihub.kyc.domain.TeacherCredentialType;
import com.manabihub.kyc.domain.TeacherQualificationStatus;

import java.time.Instant;
import java.util.UUID;

public record TeacherQualificationResponse(
        UUID id,
        UUID teacherId,
        String teacherName,
        String teacherEmail,
        LearningLanguage learningLanguage,
        ProficiencyFramework proficiencyFramework,
        String frameworkVersion,
        String credentialLevel,
        TeacherCredentialType credentialType,
        String maskedCredentialCode,
        String issuer,
        String evidenceFileName,
        String evidenceUrl,
        TeacherQualificationStatus status,
        String reviewNote,
        Instant submittedAt,
        Instant reviewedAt
) {
}
