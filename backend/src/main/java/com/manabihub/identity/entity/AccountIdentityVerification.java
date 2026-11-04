package com.manabihub.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "account_identity_verifications")
@Getter
@Setter
@NoArgsConstructor
public class AccountIdentityVerification {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "identity_fingerprint", nullable = false, unique = true, length = 64)
    private String identityFingerprint;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "source_subject", nullable = false, length = 16)
    private String sourceSubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
