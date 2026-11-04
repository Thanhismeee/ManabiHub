package com.manabihub.payout.repository;

import com.manabihub.payout.entity.WithdrawalOtpChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WithdrawalOtpChallengeRepository
        extends JpaRepository<WithdrawalOtpChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from WithdrawalOtpChallenge challenge where challenge.userId = :userId")
    Optional<WithdrawalOtpChallenge> findByUserIdForUpdate(@Param("userId") UUID userId);
}
