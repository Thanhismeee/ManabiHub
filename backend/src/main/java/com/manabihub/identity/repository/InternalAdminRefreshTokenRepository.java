package com.manabihub.identity.repository;

import com.manabihub.identity.entity.InternalAdminRefreshToken;
import com.manabihub.identity.enums.InternalAdminRefreshTokenStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InternalAdminRefreshTokenRepository
        extends JpaRepository<InternalAdminRefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select refreshToken
            from InternalAdminRefreshToken refreshToken
            where refreshToken.tokenHash = :tokenHash
            """)
    Optional<InternalAdminRefreshToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update InternalAdminRefreshToken refreshToken
            set refreshToken.status = :status,
                refreshToken.usedAt = :usedAt
            where refreshToken.sessionId = :sessionId
              and refreshToken.status = com.manabihub.identity.enums.InternalAdminRefreshTokenStatus.ACTIVE
            """)
    int revokeActiveForSession(
            @Param("sessionId") UUID sessionId,
            @Param("status") InternalAdminRefreshTokenStatus status,
            @Param("usedAt") Instant usedAt
    );


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update InternalAdminRefreshToken refreshToken
            set refreshToken.status = :status,
                refreshToken.usedAt = :usedAt
            where refreshToken.status = com.manabihub.identity.enums.InternalAdminRefreshTokenStatus.ACTIVE
              and refreshToken.sessionId in (
                  select session.id
                  from InternalAdminSession session
                  where session.adminAccountId = :adminAccountId
              )
            """)
    int revokeActiveForAccount(
            @Param("adminAccountId") UUID adminAccountId,
            @Param("status") InternalAdminRefreshTokenStatus status,
            @Param("usedAt") Instant usedAt
    );
}
