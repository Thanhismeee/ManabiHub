package com.manabihub.identity.repository;

import com.manabihub.identity.entity.AccountIdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountIdentityVerificationRepository
        extends JpaRepository<AccountIdentityVerification, UUID> {

    Optional<AccountIdentityVerification> findByIdentityFingerprint(String identityFingerprint);
}
