package com.manabihub.identity.mapper;

import com.manabihub.common.util.PhoneNumberNormalizer;
import com.manabihub.identity.dto.request.UpdateStudentProfileRequest;
import com.manabihub.identity.dto.response.StudentProfileResponse;
import com.manabihub.identity.dto.response.StudentLearningGoalResponse;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.entity.StudentLearningGoal;
import com.manabihub.course.domain.CourseLanguageProfile;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import org.springframework.stereotype.Component;

@Component
public class StudentProfileMapper {

    /**
     * Convert AppUser + StudentProfile -> StudentProfileResponse
     */
    public StudentProfileResponse toResponse(
            AppUser user,
            StudentProfile profile
    ) {

        StudentProfileResponse response = new StudentProfileResponse();

        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setPhoneVerified(user.getPhoneVerifiedAt() != null);
        response.setPhoneVerifiedAt(user.getPhoneVerifiedAt());
        response.setAvatarUrl(user.getAvatarUrl());

        response.setDisplayName(profile.getDisplayName());
        response.setJlptGoal(profile.getJlptGoal());
        response.setLearningGoals(profile.getLearningGoals().stream()
                .map(goal -> StudentLearningGoalResponse.builder()
                        .id(goal.getId())
                        .learningLanguage(goal.getLearningLanguage())
                        .proficiencyFramework(goal.getProficiencyFramework())
                        .frameworkVersion(goal.getFrameworkVersion())
                        .proficiencyLevel(goal.getProficiencyLevel())
                        .primaryGoal(goal.isPrimaryGoal())
                        .build())
                .toList());

        return response;
    }

    /**
     * Update StudentProfile from request
     */
    public void updateProfile(
            StudentProfile profile,
            UpdateStudentProfileRequest request
    ) {

        profile.setDisplayName(request.getDisplayName());
        if (request.getLearningGoals() != null) {
            replaceLearningGoals(profile, request);
        } else {
            profile.setJlptGoal(normalizeLegacyJlptGoal(request.getJlptGoal()));
            syncLegacyJlptGoal(profile);
        }
    }

    private void replaceLearningGoals(StudentProfile profile, UpdateStudentProfileRequest request) {
        profile.getLearningGoals().clear();
        java.util.Set<String> seen = new java.util.HashSet<>();
        long primaryCount = request.getLearningGoals().stream().filter(goal -> goal.isPrimaryGoal()).count();
        if (primaryCount > 1) {
            throw new com.manabihub.common.exception.BusinessException(
                    com.manabihub.common.constants.MessageCodes.VALIDATION_FAILED,
                    "Chỉ được chọn một mục tiêu học tập chính.",
                    org.springframework.http.HttpStatus.BAD_REQUEST
            );
        }

        for (var requested : request.getLearningGoals()) {
            CourseLanguageProfile normalized = CourseLanguageProfile.resolve(
                    requested.getLearningLanguage(),
                    requested.getProficiencyFramework(),
                    requested.getFrameworkVersion(),
                    requested.getProficiencyLevel(),
                    null
            );
            String key = normalized.learningLanguage() + "|" + normalized.proficiencyFramework()
                    + "|" + normalized.frameworkVersion() + "|" + normalized.proficiencyLevel();
            if (!seen.add(key)) {
                throw new com.manabihub.common.exception.BusinessException(
                        com.manabihub.common.constants.MessageCodes.VALIDATION_FAILED,
                        "Mục tiêu học tập bị trùng.",
                        org.springframework.http.HttpStatus.BAD_REQUEST
                );
            }
            profile.getLearningGoals().add(StudentLearningGoal.builder()
                    .studentProfile(profile)
                    .learningLanguage(normalized.learningLanguage())
                    .proficiencyFramework(normalized.proficiencyFramework())
                    .frameworkVersion(normalized.frameworkVersion())
                    .proficiencyLevel(normalized.proficiencyLevel())
                    .primaryGoal(requested.isPrimaryGoal())
                    .build());
        }

        profile.setJlptGoal(profile.getLearningGoals().stream()
                .filter(goal -> goal.getLearningLanguage() == LearningLanguage.JAPANESE)
                .filter(goal -> goal.getProficiencyFramework() == ProficiencyFramework.JLPT)
                .map(StudentLearningGoal::getProficiencyLevel)
                .findFirst()
                .orElse(null));
    }

    private void syncLegacyJlptGoal(StudentProfile profile) {
        String level = profile.getJlptGoal();
        profile.getLearningGoals().removeIf(goal ->
                goal.getLearningLanguage() == LearningLanguage.JAPANESE
                        && goal.getProficiencyFramework() == ProficiencyFramework.JLPT);
        if (level == null) {
            return;
        }
        profile.getLearningGoals().add(StudentLearningGoal.builder()
                .studentProfile(profile)
                .learningLanguage(LearningLanguage.JAPANESE)
                .proficiencyFramework(ProficiencyFramework.JLPT)
                .frameworkVersion("CURRENT")
                .proficiencyLevel(level)
                .primaryGoal(profile.getLearningGoals().stream().noneMatch(StudentLearningGoal::isPrimaryGoal))
                .build());
    }

    private String normalizeLegacyJlptGoal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        CourseLanguageProfile.resolve(
                LearningLanguage.JAPANESE,
                ProficiencyFramework.JLPT,
                "CURRENT",
                normalized,
                null
        );
        return normalized;
    }

    /**
     * Update AppUser from request
     */
    public void updateUser(
            AppUser user,
            UpdateStudentProfileRequest request
    ) {

        user.setFullName(request.getFullName());
        user.setPhoneNumber(PhoneNumberNormalizer.normalize(request.getPhoneNumber()));
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
    }

}
