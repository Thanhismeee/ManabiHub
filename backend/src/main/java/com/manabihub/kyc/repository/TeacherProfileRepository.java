package com.manabihub.kyc.repository;

import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.UserStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, UUID> {

    @Override
    @EntityGraph(attributePaths = "user")
    Optional<TeacherProfile> findById(UUID id);

    Optional<TeacherProfile> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM TeacherProfile p WHERE p.user.id = :userId")
    Optional<TeacherProfile> findForUpdateByUserId(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = "user")
    Optional<TeacherProfile> findByIdAndKycStatusAndCanPublishCourseTrueAndUser_UserStatus(
            UUID id,
            TeacherKycStatus kycStatus,
            UserStatus userStatus
    );

    @EntityGraph(attributePaths = "user")
    @Query("""
            SELECT profile
            FROM TeacherProfile profile
            WHERE profile.kycStatus = :kycStatus
              AND profile.canPublishCourse = true
              AND profile.user.userStatus = :userStatus
              AND EXISTS (
                  SELECT course.id
                  FROM Course course
                  WHERE course.teacher = profile
                    AND course.status = com.manabihub.course.enums.CourseStatus.PUBLISHED
              )
            ORDER BY profile.updatedAt DESC, profile.id ASC
            """)
    List<TeacherProfile> findDiscoverableProfiles(
            @Param("kycStatus") TeacherKycStatus kycStatus,
            @Param("userStatus") UserStatus userStatus,
            Pageable pageable
    );

    @Modifying
    @Query(value = """
            INSERT INTO teacher_profiles (
                id,
                user_id,
                display_name,
                kyc_status,
                can_publish_course
            )
            SELECT
                :profileId,
                app_user.id,
                app_user.full_name,
                'NOT_SUBMITTED',
                FALSE
            FROM app_users app_user
            WHERE app_user.id = :userId
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int createCandidateIfAbsent(
            @Param("profileId") UUID profileId,
            @Param("userId") UUID userId
    );
}
