package com.manabihub.course.service.impl;

import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.course.dto.response.PublicTeacherProfileResponse;
import com.manabihub.course.dto.response.PublicTeacherSummaryResponse;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.CourseModule;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.enums.LessonBlockType;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.kyc.domain.AppUser;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicTeacherProfileServiceImplTest {

    @Mock
    private TeacherProfileRepository teacherProfileRepository;

    @Mock
    private KycRequestRepository kycRequestRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseReviewService courseReviewService;

    private PublicTeacherProfileServiceImpl service;
    private TeacherProfile profile;

    @BeforeEach
    void setUp() {
        service = new PublicTeacherProfileServiceImpl(
                teacherProfileRepository,
                kycRequestRepository,
                courseRepository,
                courseReviewService
        );

        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail("private@example.com");
        user.setPhoneNumber("0900000000");
        user.setAvatarUrl("/uploads/user-avatars/public.png");
        user.setUserStatus(UserStatus.ACTIVE);

        profile = new TeacherProfile();
        profile.setId(UUID.randomUUID());
        profile.setUser(user);
        profile.setDisplayName("  Sensei An  ");
        profile.setBio("N5 grammar teacher");
        profile.setKycStatus(TeacherKycStatus.APPROVED);
        profile.setCanPublishCourse(true);
    }

    @Test
    void getProfile_UsesStrictEligibilityAndReturnsPublishedCoursesOnly() {
        LessonBlock visibleLesson = LessonBlock.builder()
                .id(UUID.randomUUID())
                .type(LessonBlockType.TEXT)
                .title("Visible lesson")
                .build();
        LessonBlock hiddenLesson = LessonBlock.builder()
                .id(UUID.randomUUID())
                .type(LessonBlockType.TEXT)
                .title("Hidden lesson")
                .moderationHidden(true)
                .build();
        Course publishedCourse = Course.builder()
                .id(UUID.randomUUID())
                .teacher(profile)
                .title("N5 Foundations")
                .slug("n5-foundations")
                .jlptLevel(JlptLevel.N5)
                .price(new BigDecimal("299000"))
                .currency("VND")
                .status(CourseStatus.PUBLISHED)
                .publishedAt(Instant.now())
                .modules(List.of(CourseModule.builder()
                        .blocks(List.of(visibleLesson, hiddenLesson))
                        .build()))
                .build();

        when(teacherProfileRepository
                .findByIdAndKycStatusAndCanPublishCourseTrueAndUser_UserStatus(
                        profile.getId(),
                        TeacherKycStatus.APPROVED,
                        UserStatus.ACTIVE
                ))
                .thenReturn(Optional.of(profile));
        when(courseRepository.findByTeacher_IdAndStatusOrderByPublishedAtDesc(
                profile.getId(),
                CourseStatus.PUBLISHED
        )).thenReturn(List.of(publishedCourse));
        KycRequest approvedCertificate = new KycRequest();
        approvedCertificate.setStatus(KycRequestStatus.APPROVED);
        approvedCertificate.setCertificateStatus(CertificateVerificationStatus.APPROVED);
        approvedCertificate.setVerificationPayload(Map.of("certificateLevel", "N5"));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(profile.getId()))
                .thenReturn(Optional.of(approvedCertificate));
        when(courseReviewService.getAggregates(List.of(publishedCourse.getId())))
                .thenReturn(Map.of(publishedCourse.getId(),
                        new CourseReviewAggregateResponse(new BigDecimal("4.5"), 2)));

        PublicTeacherProfileResponse response = service.getProfile(profile.getId());

        assertEquals(profile.getId(), response.id());
        assertEquals("Sensei An", response.displayName());
        assertEquals("/uploads/user-avatars/public.png", response.avatarUrl());
        assertEquals("N5 grammar teacher", response.bio());
        assertTrue(response.verified());
        assertEquals(1, response.publishedCourseCount());
        assertEquals(1, response.credentials().size());
        assertEquals("JLPT", response.credentials().getFirst().type());
        assertEquals("N5", response.credentials().getFirst().level());
        assertEquals("APPROVED", response.credentials().getFirst().verificationStatus());
        assertEquals(new BigDecimal("4.50"), response.ratingSummary().averageRating());
        assertEquals(2, response.ratingSummary().reviewCount());
        assertEquals("n5-foundations", response.courses().getFirst().slug());
        assertEquals(1, response.courses().getFirst().totalLessons());

        verify(courseRepository).findByTeacher_IdAndStatusOrderByPublishedAtDesc(
                profile.getId(),
                CourseStatus.PUBLISHED
        );
    }

    @Test
    void getProfile_OnlyExposesApprovedCertificateAndCombinesCourseRatings() {
        Course firstCourse = Course.builder().id(UUID.randomUUID()).modules(List.of()).build();
        Course secondCourse = Course.builder().id(UUID.randomUUID()).modules(List.of()).build();
        when(teacherProfileRepository
                .findByIdAndKycStatusAndCanPublishCourseTrueAndUser_UserStatus(
                        profile.getId(), TeacherKycStatus.APPROVED, UserStatus.ACTIVE))
                .thenReturn(Optional.of(profile));
        when(courseRepository.findByTeacher_IdAndStatusOrderByPublishedAtDesc(
                profile.getId(), CourseStatus.PUBLISHED)).thenReturn(List.of(firstCourse, secondCourse));
        when(kycRequestRepository.findTopByTeacherProfileIdOrderBySubmittedAtDesc(profile.getId()))
                .thenReturn(Optional.empty());
        when(courseReviewService.getAggregates(List.of(firstCourse.getId(), secondCourse.getId())))
                .thenReturn(Map.of(
                        firstCourse.getId(), new CourseReviewAggregateResponse(new BigDecimal("4.0"), 1),
                        secondCourse.getId(), new CourseReviewAggregateResponse(new BigDecimal("5.0"), 3)
                ));

        PublicTeacherProfileResponse response = service.getProfile(profile.getId());

        assertTrue(response.credentials().isEmpty());
        assertEquals(new BigDecimal("4.75"), response.ratingSummary().averageRating());
        assertEquals(4, response.ratingSummary().reviewCount());
    }

    @Test
    void getProfile_WhenTeacherIsNotDiscoverable_ReturnsGenericNotFound() {
        when(teacherProfileRepository
                .findByIdAndKycStatusAndCanPublishCourseTrueAndUser_UserStatus(
                        profile.getId(),
                        TeacherKycStatus.APPROVED,
                        UserStatus.ACTIVE
                ))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getProfile(profile.getId())
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
        assertEquals(MessageCodes.MSG_CATALOG_001, exception.getMessageCode());
        assertEquals("Teacher profile was not found", exception.getMessage());
    }

    @Test
    void listFeatured_UsesDiscoverableProfilesAndPublishedCounts() {
        when(teacherProfileRepository.findDiscoverableProfiles(
                TeacherKycStatus.APPROVED,
                UserStatus.ACTIVE,
                PageRequest.of(0, 3)
        )).thenReturn(List.of(profile));
        when(courseRepository.countByTeacher_IdAndStatus(profile.getId(), CourseStatus.PUBLISHED))
                .thenReturn(2L);

        List<PublicTeacherSummaryResponse> response = service.listFeatured(3);

        assertEquals(1, response.size());
        assertEquals(profile.getId(), response.getFirst().id());
        assertEquals(2L, response.getFirst().publishedCourseCount());
        assertTrue(response.getFirst().verified());
    }
}
