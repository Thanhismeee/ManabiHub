package com.manabihub.kyc.repository;

import com.manabihub.kyc.domain.TeacherIdentityClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeacherIdentityClaimRepository extends JpaRepository<TeacherIdentityClaim, UUID> {

    Optional<TeacherIdentityClaim> findByIdentityFingerprint(String identityFingerprint);

    Optional<TeacherIdentityClaim> findByTeacherId(UUID teacherId);
}
