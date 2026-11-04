package com.manabihub.identity.repository;

import com.manabihub.identity.entity.InternalAdminPasswordReset;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InternalAdminPasswordResetRepository
        extends JpaRepository<InternalAdminPasswordReset, UUID> {

    Optional<InternalAdminPasswordReset> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select passwordReset
            from InternalAdminPasswordReset passwordReset
            where passwordReset.tokenHash = :tokenHash
            """)
    Optional<InternalAdminPasswordReset> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Query("""
            select passwordReset
            from InternalAdminPasswordReset passwordReset
            where passwordReset.adminAccountId = :adminAccountId
              and passwordReset.usedAt is null
              and passwordReset.revokedAt is null
            """)
    Optional<InternalAdminPasswordReset> findOpenForAccount(
            @Param("adminAccountId") UUID adminAccountId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update InternalAdminPasswordReset passwordReset
            set passwordReset.revokedAt = :revokedAt
            where passwordReset.adminAccountId = :adminAccountId
              and passwordReset.usedAt is null
              and passwordReset.revokedAt is null
            """)
    int revokeOpenForAccount(
            @Param("adminAccountId") UUID adminAccountId,
            @Param("revokedAt") Instant revokedAt
    );
}
