package com.manabihub.kyc.repository;

import com.manabihub.kyc.domain.TeacherCertificateClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeacherCertificateClaimRepository extends JpaRepository<TeacherCertificateClaim, UUID> {

    Optional<TeacherCertificateClaim> findByCertificateTypeAndNormalizedCertificateCode(
            String certificateType,
            String normalizedCertificateCode
    );
}
