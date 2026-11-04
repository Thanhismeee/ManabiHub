package com.manabihub.identity.repository;

import com.manabihub.identity.entity.PublicUserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicUserSessionRepository extends JpaRepository<PublicUserSession, UUID> {

    @Query("SELECT s FROM PublicUserSession s WHERE s.userId = :userId AND s.revokedAt IS NULL AND s.expiresAt > :now")
    List<PublicUserSession> findActiveSessionsByUserId(UUID userId, Instant now);

    @Modifying
    @Query("UPDATE PublicUserSession s SET s.revokedAt = :revokedAt WHERE s.deviceId = :deviceId AND s.revokedAt IS NULL")
    void revokeAllByDeviceId(UUID deviceId, Instant revokedAt);

    @Modifying
    @Query("UPDATE PublicUserSession s SET s.revokedAt = :revokedAt WHERE s.userId = :userId AND s.revokedAt IS NULL")
    void revokeAllByUserId(UUID userId, Instant revokedAt);
}
