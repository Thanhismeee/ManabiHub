package com.manabihub.identity.entity;

import com.manabihub.common.enums.PhoneOtpMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "phone_verification_challenges")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhoneVerificationChallenge {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "challenge_id", nullable = false, unique = true)
    private UUID challengeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", nullable = false, length = 20)
    private PhoneOtpMethod verificationMethod;

    @Column(name = "code_hash", length = 128)
    private String codeHash;

    @Column(length = 64)
    private String nonce;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resend_available_at", nullable = false)
    private Instant resendAvailableAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
