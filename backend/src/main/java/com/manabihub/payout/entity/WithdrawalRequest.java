package com.manabihub.payout.entity;

import com.manabihub.payout.enums.WithdrawalStatus;
import com.manabihub.wallet.enums.WalletOwnerType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "withdrawal_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalRequest {

    @Id
    @UuidGenerator
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.UUID)
    private java.util.UUID id;

    @Column(name = "teacher_id")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.UUID)
    private java.util.UUID teacherId;

    @Column(name = "student_id")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.UUID)
    private java.util.UUID studentId;

    @Column(name = "wallet_id", nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.UUID)
    private java.util.UUID walletId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 30)
    private WalletOwnerType ownerType = WalletOwnerType.TEACHER;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private WithdrawalStatus status = WithdrawalStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bank_account_snapshot", columnDefinition = "jsonb")
    private BankAccountSnapshot bankAccountSnapshot;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "bank_qr_code", columnDefinition = "bytea")
    private byte[] bankQrCode;

    @Column(name = "bank_qr_content_type", length = 50)
    private String bankQrContentType;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_by")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.UUID)
    private java.util.UUID decidedBy;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
