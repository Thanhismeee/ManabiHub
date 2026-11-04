package com.manabihub.kyc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "vnpt_identity_transaction_claims",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_vnpt_identity_claim_provider_transaction",
                columnNames = {"provider", "provider_transaction_id"}))
@Getter
@Setter
@NoArgsConstructor
public class VnptIdentityTransactionClaim {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "subject_type", nullable = false, length = 16)
    private String subjectType;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "provider_transaction_id", nullable = false, length = 128)
    private String providerTransactionId;

    @Column(name = "provider_session_id", length = 128)
    private String providerSessionId;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;
}
