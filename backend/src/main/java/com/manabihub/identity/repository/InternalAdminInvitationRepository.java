package com.manabihub.identity.repository;

import com.manabihub.identity.entity.InternalAdminInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InternalAdminInvitationRepository
        extends JpaRepository<InternalAdminInvitation, UUID> {

    Optional<InternalAdminInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from InternalAdminInvitation invitation
            where invitation.tokenHash = :tokenHash
            """)
    Optional<InternalAdminInvitation> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update InternalAdminInvitation invitation
            set invitation.revokedAt = :revokedAt
            where invitation.adminAccountId = :adminAccountId
              and invitation.usedAt is null
              and invitation.revokedAt is null
            """)
    int revokeOpenInvitations(
            @Param("adminAccountId") UUID adminAccountId,
            @Param("revokedAt") Instant revokedAt
    );

    List<InternalAdminInvitation> findAllByAdminAccountIdInOrderByCreatedAtDesc(
            Collection<UUID> adminAccountIds
    );
}
