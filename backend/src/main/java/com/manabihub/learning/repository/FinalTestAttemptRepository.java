package com.manabihub.learning.repository;

import com.manabihub.learning.entity.FinalTestAttempt;
import com.manabihub.learning.enums.FinalTestAttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FinalTestAttemptRepository extends JpaRepository<FinalTestAttempt, UUID> {

    long countByEnrollmentIdAndFinalTestId(UUID enrollmentId, UUID finalTestId);

    boolean existsByEnrollmentIdAndFinalTestIdAndPassedTrue(UUID enrollmentId, UUID finalTestId);

    Optional<FinalTestAttempt> findFirstByEnrollmentIdAndFinalTestIdAndStatusOrderByStartedAtDesc(
            UUID enrollmentId,
            UUID finalTestId,
            FinalTestAttemptStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT attempt
            FROM FinalTestAttempt attempt
            WHERE attempt.id = :attemptId
              AND attempt.enrollment.id = :enrollmentId
            """)
    Optional<FinalTestAttempt> findOwnedByIdForUpdate(
            @Param("attemptId") UUID attemptId,
            @Param("enrollmentId") UUID enrollmentId
    );
}
