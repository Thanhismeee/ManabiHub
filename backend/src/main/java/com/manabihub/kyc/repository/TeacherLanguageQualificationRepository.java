package com.manabihub.kyc.repository;

import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import com.manabihub.kyc.domain.TeacherLanguageQualification;
import com.manabihub.kyc.domain.TeacherQualificationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherLanguageQualificationRepository
        extends JpaRepository<TeacherLanguageQualification, UUID> {

    @EntityGraph(attributePaths = {"teacher", "teacher.user"})
    List<TeacherLanguageQualification> findByTeacher_User_IdOrderBySubmittedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"teacher", "teacher.user"})
    List<TeacherLanguageQualification> findByStatusOrderBySubmittedAtAsc(
            TeacherQualificationStatus status);

    @EntityGraph(attributePaths = {"teacher", "teacher.user"})
    Optional<TeacherLanguageQualification> findByIdAndTeacher_User_Id(UUID id, UUID userId);

    boolean existsByTeacher_IdAndLearningLanguageAndProficiencyFrameworkAndStatus(
            UUID teacherId,
            LearningLanguage learningLanguage,
            ProficiencyFramework proficiencyFramework,
            TeacherQualificationStatus status);

    boolean existsByTeacher_IdAndStatus(UUID teacherId, TeacherQualificationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from TeacherLanguageQualification q where q.id = :id")
    Optional<TeacherLanguageQualification> findForUpdate(@Param("id") UUID id);

    @Query("""
            select qualification
            from TeacherLanguageQualification qualification
            where upper(qualification.credentialCode) = upper(:credentialCode)
              and qualification.proficiencyFramework = :framework
              and qualification.status in :statuses
            """)
    List<TeacherLanguageQualification> findCredentialClaims(
            @Param("framework") ProficiencyFramework framework,
            @Param("credentialCode") String credentialCode,
            @Param("statuses") Collection<TeacherQualificationStatus> statuses);
}
