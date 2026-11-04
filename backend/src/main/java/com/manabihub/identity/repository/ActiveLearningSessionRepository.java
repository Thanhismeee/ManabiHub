package com.manabihub.identity.repository;

import com.manabihub.identity.entity.ActiveLearningSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ActiveLearningSessionRepository extends JpaRepository<ActiveLearningSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ActiveLearningSession a WHERE a.userId = :userId")
    Optional<ActiveLearningSession> findByIdForUpdate(UUID userId);
    
    @Modifying
    @Query("DELETE FROM ActiveLearningSession a WHERE a.publicSessionId = :sessionId")
    void deleteByPublicSessionId(UUID sessionId);
    
    @Modifying
    @Query("DELETE FROM ActiveLearningSession a WHERE a.publicSessionId IN (SELECT s.id FROM PublicUserSession s WHERE s.deviceId = :deviceId)")
    void deleteByDeviceId(UUID deviceId);
}
