package com.manabihub.kyc.repository;

import com.manabihub.kyc.domain.KycRequest;
import com.manabihub.kyc.domain.KycRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycRequestRepository extends JpaRepository<KycRequest, UUID> {
    List<KycRequest> findByStatusOrderByCreatedAtDesc(KycRequestStatus status);
    Optional<KycRequest> findTopByTeacherProfileIdOrderBySubmittedAtDesc(UUID teacherId);
    Optional<KycRequest> findByEkycProviderAndProviderTransactionId(String ekycProvider, String providerTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT request
            FROM KycRequest request
            JOIN FETCH request.teacherProfile teacher
            JOIN FETCH teacher.user
            WHERE request.id = :id
            """)
    Optional<KycRequest> findByIdForReview(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM KycRequest r WHERE r.id = :id")
    Optional<KycRequest> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByEkycProviderAndProviderTransactionId(String ekycProvider, String txId);
    boolean existsByTeacherProfileIdAndStatus(UUID teacherId, KycRequestStatus status);
}
