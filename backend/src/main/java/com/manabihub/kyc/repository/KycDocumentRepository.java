package com.manabihub.kyc.repository;

import com.manabihub.kyc.domain.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    List<KycDocument> findByKycRequestIdOrderByCreatedAtAsc(UUID kycRequestId);

    Optional<KycDocument> findByIdAndKycRequestId(UUID id, UUID kycRequestId);
}
