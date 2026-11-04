package com.manabihub.kyc.repository;

import com.manabihub.kyc.domain.VnptIdentityTransactionClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface VnptIdentityTransactionClaimRepository
        extends JpaRepository<VnptIdentityTransactionClaim, UUID> {

    Optional<VnptIdentityTransactionClaim> findByProviderAndProviderTransactionId(
            String provider,
            String providerTransactionId
    );
}
