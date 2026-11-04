package com.manabihub.course.service.impl;

import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.dto.response.PublicTeacherProfileResponse;
import com.manabihub.course.dto.response.PublicTeacherSummaryResponse;
import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.service.PublicTeacherProfileService;
import com.manabihub.kyc.domain.AppUser;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.domain.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@Transactional
class PublicTeacherProfilePostgresIntegrationTest {

    private static PostgreSQLContainer<?> postgresContainer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine");
        postgresContainer.start();
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private PublicTeacherProfileService publicTeacherProfileService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void discoveryQueries_ExposeOnlyEligibleTeachersAndPublishedCourses() {
        TeacherProfile discoverable = createProfile(
                "discoverable",
                UserStatus.ACTIVE,
                TeacherKycStatus.APPROVED,
                true
        );
        TeacherProfile revoked = createProfile(
                "revoked",
                UserStatus.ACTIVE,
                TeacherKycStatus.REVOKED,
                false
        );
        TeacherProfile locked = createProfile(
                "locked",
                UserStatus.LOCKED,
                TeacherKycStatus.APPROVED,
                true
        );

        createCourse(discoverable, "published-course", CourseStatus.PUBLISHED);
        createCourse(discoverable, "draft-course", CourseStatus.DRAFT);
        createCourse(revoked, "revoked-course", CourseStatus.PUBLISHED);
        createCourse(locked, "locked-course", CourseStatus.PUBLISHED);
        entityManager.flush();
        entityManager.clear();

        PublicTeacherProfileResponse profile =
                publicTeacherProfileService.getProfile(discoverable.getId());

        assertEquals(discoverable.getId(), profile.id());
        assertEquals(1, profile.publishedCourseCount());
        assertEquals(List.of("published-course"), profile.courses().stream()
                .map(course -> course.title())
                .toList());

        assertThrows(BusinessException.class, () -> publicTeacherProfileService.getProfile(revoked.getId()));
        assertThrows(BusinessException.class, () -> publicTeacherProfileService.getProfile(locked.getId()));

        List<PublicTeacherSummaryResponse> featured = publicTeacherProfileService.listFeatured(12);
        assertTrue(featured.stream().anyMatch(item -> item.id().equals(discoverable.getId())));
        assertFalse(featured.stream().anyMatch(item -> item.id().equals(revoked.getId())));
        assertFalse(featured.stream().anyMatch(item -> item.id().equals(locked.getId())));
    }

    private TeacherProfile createProfile(
            String label,
            UserStatus userStatus,
            TeacherKycStatus kycStatus,
            boolean canPublishCourse
    ) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail(label + "-" + UUID.randomUUID() + "@example.test");
        user.setFullName("Private legal name");
        user.setPhoneNumber(switch (label) {
            case "discoverable" -> "0900000001";
            case "revoked" -> "0900000002";
            case "locked" -> "0900000003";
            default -> "0900000009";
        });
        user.setUserStatus(userStatus);
        user.setProvider("LOCAL");
        entityManager.persist(user);

        TeacherProfile profile = new TeacherProfile();
        profile.setId(UUID.randomUUID());
        profile.setUser(user);
        profile.setDisplayName("Public " + label);
        profile.setBio("Public bio");
        profile.setKycStatus(kycStatus);
        profile.setCanPublishCourse(canPublishCourse);
        entityManager.persist(profile);
        return profile;
    }

    private void createCourse(TeacherProfile teacher, String slug, CourseStatus status) {
        Course course = Course.builder()
                .teacher(teacher)
                .title(slug)
                .slug(slug + "-" + UUID.randomUUID())
                .description("Public course")
                .price(new BigDecimal("299000"))
                .currency("VND")
                .status(status)
                .publishedAt(status == CourseStatus.PUBLISHED ? Instant.now() : null)
                .build();
        entityManager.persist(course);
    }
}
