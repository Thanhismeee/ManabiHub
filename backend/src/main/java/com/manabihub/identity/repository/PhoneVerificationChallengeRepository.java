package com.manabihub.identity.repository;

import com.manabihub.identity.entity.PhoneVerificationChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface PhoneVerificationChallengeRepository extends JpaRepository<PhoneVerificationChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from PhoneVerificationChallenge challenge where challenge.userId = :userId")
    Optional<PhoneVerificationChallenge> findByUserIdForUpdate(@Param("userId") UUID userId);
}
