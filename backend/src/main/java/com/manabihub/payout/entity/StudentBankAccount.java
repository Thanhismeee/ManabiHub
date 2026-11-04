package com.manabihub.payout.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_bank_accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentBankAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "bank_code", nullable = false, length = 50)
    private String bankCode;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 512)
    private String accountNumber;

    @Column(name = "account_fingerprint", nullable = false, length = 64)
    private String accountFingerprint;

    @Column(name = "account_holder_name", nullable = false)
    private String accountHolderName;

    @Column(name = "branch")
    private String branch;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Builder.Default
    @Column(name = "ownership_verified", nullable = false)
    private boolean ownershipVerified = false;

    @Column(name = "ownership_verification_method", length = 50)
    private String ownershipVerificationMethod;

    @Column(name = "ownership_verified_at")
    private LocalDateTime ownershipVerifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
