package com.manabihub.identity.repository;

import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.enums.RoleCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository("identityInternalAdminAccountRepository")
public interface InternalAdminAccountRepository extends JpaRepository<InternalAdminAccount, UUID> {
    Optional<InternalAdminAccount> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from IdentityInternalAdminAccount account
            where lower(account.email) = lower(:email)
            """)
    Optional<InternalAdminAccount> findByEmailIgnoreCaseForUpdate(@Param("email") String email);

    @EntityGraph(attributePaths = "role")
    List<InternalAdminAccount> findAllByOrderByFullNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from IdentityInternalAdminAccount account
            join fetch account.role
            where account.id = :accountId
            """)
    Optional<InternalAdminAccount> findByIdForRoleUpdate(@Param("accountId") UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from IdentityInternalAdminAccount account
            where account.accountStatus = :status
              and account.role.code = :roleCode
            order by account.id
            """)
    List<InternalAdminAccount> findAllByStatusAndRoleCodeForUpdate(
            @Param("status") AccountStatus status,
            @Param("roleCode") RoleCode roleCode
    );

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM internal_admin_accounts account
                JOIN internal_admin_roles assignment
                  ON assignment.admin_account_id = account.id
                JOIN role_permissions role_permission
                  ON role_permission.role_id = assignment.role_id
                JOIN permissions permission
                  ON permission.id = role_permission.permission_id
                WHERE account.id = :adminId
                  AND account.account_status = 'ACTIVE'
                  AND permission.code = :permissionCode
            )
            """, nativeQuery = true)
    boolean hasPermission(
            @Param("adminId") UUID adminId,
            @Param("permissionCode") String permissionCode
    );

    @Query("SELECT a.id FROM IdentityInternalAdminAccount a WHERE lower(a.email) LIKE lower(concat('%', :search, '%')) OR lower(a.fullName) LIKE lower(concat('%', :search, '%'))")
    List<UUID> searchAdminIds(@Param("search") String search, org.springframework.data.domain.Pageable pageable);
}
