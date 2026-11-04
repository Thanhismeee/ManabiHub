package com.manabihub.course.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.dto.response.PublicTeacherCourseResponse;
import com.manabihub.course.dto.response.PublicTeacherCredentialResponse;
import com.manabihub.course.dto.response.PublicTeacherProfileResponse;
import com.manabihub.course.dto.response.PublicTeacherRatingSummaryResponse;
import com.manabihub.course.dto.response.PublicTeacherSummaryResponse;
import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.service.PublicTeacherProfileService;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.domain.UserStatus;
import com.manabihub.kyc.domain.CertificateVerificationStatus;
import com.manabihub.kyc.domain.KycRequest;
import com.manabihub.kyc.domain.KycRequestStatus;
import com.manabihub.kyc.repository.KycRequestRepository;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.review.dto.response.CourseReviewAggregateResponse;
import com.manabihub.review.service.CourseReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicTeacherProfileServiceImpl implements PublicTeacherProfileService {

    private static final String PRIVATE_PROFILE_MESSAGE = "Teacher profile was not found";
    private static final String FALLBACK_DISPLAY_NAME = "Giảng viên ManabiHub";

    private final TeacherProfileRepository teacherProfileRepository;
    private final KycRequestRepository kycRequestRepository;
    private final CourseRepository courseRepository;
    private final CourseReviewService courseReviewService;

    @Override
    @Transactional(readOnly = true)
    public PublicTeacherProfileResponse getProfile(UUID teacherId) {
        TeacherProfile profile = findDiscoverableProfile(teacherId);
        List<Course> publishedCourses = courseRepository
                .findByTeacher_IdAndStatusOrderByPublishedAtDesc(
                        teacherId,
                        CourseStatus.PUBLISHED
                );
        Map<UUID, CourseReviewAggregateResponse> aggregates =
                courseReviewService.getAggregates(
                        publishedCourses.stream().map(Course::getId).toList()
                );
        List<PublicTeacherCourseResponse> courses = publishedCourses.stream()
                .map(course -> toCourseResponse(
                        course,
                        aggregates.getOrDefault(
                                course.getId(),
                                CourseReviewAggregateResponse.empty()
                        )
                ))
                .toList();

        return new PublicTeacherProfileResponse(
                profile.getId(),
                publicDisplayName(profile),
                profile.getUser().getAvatarUrl(),
                profile.getBio(),
                true,
                courses.size(),
                credentialsFor(profile.getId()),
                ratingSummaryFor(aggregates),
                courses
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicTeacherSummaryResponse> listFeatured(int limit) {
        return teacherProfileRepository.findDiscoverableProfiles(
                        TeacherKycStatus.APPROVED,
                        UserStatus.ACTIVE,
                        PageRequest.of(0, limit)
                )
                .stream()
                .map(profile -> new PublicTeacherSummaryResponse(
                        profile.getId(),
                        publicDisplayName(profile),
                        profile.getUser().getAvatarUrl(),
                        profile.getBio(),
                        true,
                        courseRepository.countByTeacher_IdAndStatus(profile.getId(), CourseStatus.PUBLISHED)
                ))
                .toList();
    }

    private TeacherProfile findDiscoverableProfile(UUID teacherId) {
        return teacherProfileRepository
                .findByIdAndKycStatusAndCanPublishCourseTrueAndUser_UserStatus(
                        teacherId,
                        TeacherKycStatus.APPROVED,
                        UserStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.MSG_CATALOG_001,
                        PRIVATE_PROFILE_MESSAGE,
                        HttpStatus.NOT_FOUND
                ));
    }

    private String publicDisplayName(TeacherProfile profile) {
        return StringUtils.hasText(profile.getDisplayName())
                ? profile.getDisplayName().trim()
                : FALLBACK_DISPLAY_NAME;
    }

    private PublicTeacherCourseResponse toCourseResponse(
            Course course,
            CourseReviewAggregateResponse reviewAggregate
    ) {
        int totalLessons = course.getModules().stream()
                .mapToInt(module -> Math.toIntExact(module.getBlocks().stream()
                        .filter(block -> !block.isModerationHidden())
                        .count()))
                .sum();

        return new PublicTeacherCourseResponse(
                course.getId(),
                course.getTitle(),
                course.getSlug(),
                course.getThumbnailUrl(),
                course.getJlptLevel(),
                course.getCategory(),
                course.getPrice(),
                course.getCurrency(),
                totalLessons,
                course.getPublishedAt(),
                reviewAggregate.averageRating(),
                reviewAggregate.reviewCount()
        );
    }

    private List<PublicTeacherCredentialResponse> credentialsFor(UUID teacherId) {
        return kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(teacherId)
                .filter(request -> request.getStatus() == KycRequestStatus.APPROVED)
                .filter(request -> request.getCertificateStatus() == CertificateVerificationStatus.APPROVED)
                .map(this::toPublicCredential)
                .map(List::of)
                .orElseGet(List::of);
    }

    private PublicTeacherCredentialResponse toPublicCredential(KycRequest request) {
        Object certificateLevel = request.getVerificationPayload().get("certificateLevel");
        String level = certificateLevel == null ? null : certificateLevel.toString().trim();
        return new PublicTeacherCredentialResponse("JLPT", level, "APPROVED");
    }

    private PublicTeacherRatingSummaryResponse ratingSummaryFor(
            Map<UUID, CourseReviewAggregateResponse> aggregates
    ) {
        long reviewCount = aggregates.values().stream()
                .mapToLong(CourseReviewAggregateResponse::reviewCount)
                .sum();
        BigDecimal weightedRating = aggregates.values().stream()
                .filter(aggregate -> aggregate.averageRating() != null && aggregate.reviewCount() > 0)
                .map(aggregate -> aggregate.averageRating()
                        .multiply(BigDecimal.valueOf(aggregate.reviewCount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageRating = reviewCount == 0
                ? BigDecimal.ZERO
                : weightedRating.divide(BigDecimal.valueOf(reviewCount), 2, RoundingMode.HALF_UP);
        return new PublicTeacherRatingSummaryResponse(averageRating, reviewCount);
    }
}
