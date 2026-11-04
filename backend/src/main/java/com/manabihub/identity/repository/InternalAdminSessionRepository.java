package com.manabihub.identity.repository;

import com.manabihub.identity.entity.InternalAdminSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InternalAdminSessionRepository
        extends JpaRepository<InternalAdminSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from InternalAdminSession session
            where session.id = :sessionId
            """)
    Optional<InternalAdminSession> findByIdForUpdate(
            @Param("sessionId") UUID sessionId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update InternalAdminSession session
            set session.revokedAt = :revokedAt
            where session.adminAccountId = :adminAccountId
              and session.revokedAt is null
            """)
    int revokeAllForAccount(
            @Param("adminAccountId") UUID adminAccountId,
            @Param("revokedAt") Instant revokedAt
    );
}
