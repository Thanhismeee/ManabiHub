package com.manabihub.wallet.entity;

import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.wallet.enums.WalletOwnerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A balance-holding wallet owned by a student, teacher, or the platform (UC-08 / wallet top-up).
 * <p>
 * Maps the {@code wallets} table (V002). Exactly one of {@code student}/{@code teacher}
 * is set for STUDENT/TEACHER wallets; both are {@code null} for the singleton PLATFORM wallet.
 */
@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 30)
    private WalletOwnerType ownerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private TeacherProfile teacher;

    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "frozen_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal frozenBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "withdrawable_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal withdrawableBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "frozen_withdrawable_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal frozenWithdrawableBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false)
    private boolean frozen = false;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String currency = "VND";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public BigDecimal getAvailableBalance() {
        return balance.subtract(frozenBalance);
    }

    public BigDecimal getAvailableWithdrawableBalance() {
        return withdrawableBalance.subtract(frozenWithdrawableBalance);
    }

    public UUID getStudentId() {
        return student == null ? null : student.getId();
    }

    public UUID getTeacherId() {
        return teacher == null ? null : teacher.getId();
    }
}
