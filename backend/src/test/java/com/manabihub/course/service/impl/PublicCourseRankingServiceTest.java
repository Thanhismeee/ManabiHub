package com.manabihub.course.service.impl;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.course.dto.response.PublicCourseSummaryResponse;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.repository.CourseCategoryRepository;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.repository.projection.PublicCourseCardProjection;
import com.manabihub.course.repository.projection.PublicCourseLessonCountProjection;
import com.manabihub.course.repository.projection.PublicCourseRankProjection;
import com.manabihub.course.service.CourseValidationService;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.kyc.repository.TeacherProfileRepository;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.notification.service.NotificationService;
import com.manabihub.review.service.CourseReviewService;
import com.manabihub.systemconfig.service.SystemSettingValueService;
import com.manabihub.wallet.repository.EscrowLedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicCourseRankingServiceTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseCategoryRepository courseCategoryRepository;
    @Mock
    private TeacherProfileRepository teacherProfileRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private CourseValidationService courseValidationService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private CourseReviewService courseReviewService;
    @Mock
    private SystemSettingValueService settingValueService;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private EscrowLedgerRepository escrowLedgerRepository;

    @InjectMocks
    private CourseServiceImpl courseService;

    @Test
    void enrollmentRanking_usesDatabaseOrderAndBatchesCardData() {
        UUID firstCourseId = UUID.randomUUID();
        UUID secondCourseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        PageRequest requestedPage = PageRequest.of(
                1,
                2,
                Sort.by(Sort.Direction.DESC, "enrollmentCount")
        );
        PageRequest databasePage = PageRequest.of(1, 2);
        RankRow firstRank = new RankRow(firstCourseId, 91, new BigDecimal("4.40"), 12);
        RankRow secondRank = new RankRow(secondCourseId, 84, new BigDecimal("4.90"), 31);

        when(courseRepository.findPublicCoursesRankedByEnrollments(
                "%kanji%",
                "KANJI",
                "N5",
                new BigDecimal("10000"),
                new BigDecimal("500000"),
                databasePage
        )).thenReturn(new PageImpl<>(List.of(firstRank, secondRank), databasePage, 7));
        // Deliberately return card rows in the opposite order: response order
        // must remain the database ranking order.
        when(courseRepository.findPublicCourseCardsByIds(List.of(firstCourseId, secondCourseId)))
                .thenReturn(List.of(
                        card(secondCourseId, teacherId, "Second ranked"),
                        card(firstCourseId, teacherId, "First ranked")
                ));
        when(courseRepository.countVisibleLessonsForPublicCourses(List.of(firstCourseId, secondCourseId)))
                .thenReturn(List.of(
                        new LessonCountRow(firstCourseId, 14),
                        new LessonCountRow(secondCourseId, 8)
                ));

        Page<PublicCourseSummaryResponse> result = courseService.searchPublicCourses(
                "  Kanji ",
                " KANJI ",
                JlptLevel.N5,
                new BigDecimal("10000"),
                new BigDecimal("500000"),
                requestedPage
        );

        assertThat(result.getTotalElements()).isEqualTo(7);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getContent()).extracting(PublicCourseSummaryResponse::getId)
                .containsExactly(firstCourseId, secondCourseId);
        assertThat(result.getContent().get(0).getEnrollmentCount()).isEqualTo(91);
        assertThat(result.getContent().get(0).getAverageRating()).isEqualByComparingTo("4.40");
        assertThat(result.getContent().get(0).getReviewCount()).isEqualTo(12);
        assertThat(result.getContent().get(0).getTotalLessons()).isEqualTo(14);
        verify(courseRepository, never()).findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<com.manabihub.course.entity.Course>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)
        );
        verify(enrollmentRepository, never()).countByCourseIdsAndStatuses(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
        verify(courseReviewService, never()).getAggregates(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ratingRanking_usesDedicatedWhitelistedQuery() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        PageRequest requestedPage = PageRequest.of(
                0,
                4,
                Sort.by(Sort.Direction.DESC, "averageRating")
        );
        PageRequest databasePage = PageRequest.of(0, 4);
        RankRow rank = new RankRow(courseId, 15, new BigDecimal("5.00"), 8);

        when(courseRepository.findPublicCoursesRankedByRating(
                null, null, null, null, null, databasePage
        )).thenReturn(new PageImpl<>(List.of(rank), databasePage, 1));
        when(courseRepository.findPublicCourseCardsByIds(List.of(courseId)))
                .thenReturn(List.of(card(courseId, teacherId, "Top rated")));
        when(courseRepository.countVisibleLessonsForPublicCourses(List.of(courseId)))
                .thenReturn(List.of(new LessonCountRow(courseId, 3)));

        Page<PublicCourseSummaryResponse> result = courseService.searchPublicCourses(
                null, null, null, null, null, requestedPage
        );

        assertThat(result.getContent()).singleElement().satisfies(course -> {
            assertThat(course.getId()).isEqualTo(courseId);
            assertThat(course.getAverageRating()).isEqualByComparingTo("5.00");
            assertThat(course.getReviewCount()).isEqualTo(8);
            assertThat(course.getEnrollmentCount()).isEqualTo(15);
        });
        verify(courseRepository, never()).findPublicCoursesRankedByEnrollments(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private static CardRow card(UUID courseId, UUID teacherId, String title) {
        return new CardRow(
                courseId,
                title,
                title.toLowerCase().replace(' ', '-'),
                "/images/course.png",
                "N5",
                "KANJI",
                new BigDecimal("199000"),
                "VND",
                teacherId,
                "Teacher",
                "/images/teacher.png",
                Instant.parse("2026-08-01T00:00:00Z")
        );
    }

    private record RankRow(
            UUID courseId,
            long enrollmentCount,
            BigDecimal averageRating,
            long reviewCount
    ) implements PublicCourseRankProjection {
        @Override
        public UUID getCourseId() {
            return courseId;
        }

        @Override
        public long getEnrollmentCount() {
            return enrollmentCount;
        }

        @Override
        public BigDecimal getAverageRating() {
            return averageRating;
        }

        @Override
        public long getReviewCount() {
            return reviewCount;
        }
    }

    private record CardRow(
            UUID courseId,
            String title,
            String slug,
            String thumbnailUrl,
            String jlptLevel,
            String category,
            BigDecimal price,
            String currency,
            UUID teacherId,
            String teacherName,
            String teacherAvatarUrl,
            Instant publishedAt
    ) implements PublicCourseCardProjection {
        @Override public UUID getCourseId() { return courseId; }
        @Override public String getTitle() { return title; }
        @Override public String getSlug() { return slug; }
        @Override public String getThumbnailUrl() { return thumbnailUrl; }
        @Override public String getJlptLevel() { return jlptLevel; }
        @Override public String getLearningLanguage() { return "JAPANESE"; }
        @Override public String getProficiencyFramework() { return "JLPT"; }
        @Override public String getFrameworkVersion() { return null; }
        @Override public String getProficiencyLevel() { return jlptLevel; }
        @Override public String getCategory() { return category; }
        @Override public BigDecimal getPrice() { return price; }
        @Override public String getCurrency() { return currency; }
        @Override public UUID getTeacherId() { return teacherId; }
        @Override public String getTeacherName() { return teacherName; }
        @Override public String getTeacherAvatarUrl() { return teacherAvatarUrl; }
        @Override public Instant getPublishedAt() { return publishedAt; }
    }

    private record LessonCountRow(UUID courseId, long totalLessons)
            implements PublicCourseLessonCountProjection {
        @Override public UUID getCourseId() { return courseId; }
        @Override public long getTotalLessons() { return totalLessons; }
    }
}
