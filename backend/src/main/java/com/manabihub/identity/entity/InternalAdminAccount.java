package com.manabihub.identity.entity;

import com.manabihub.identity.enums.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "IdentityInternalAdminAccount")
@Table(name = "internal_admin_accounts")
@Getter
@Setter
public class InternalAdminAccount {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "credential_version", nullable = false)
    private long credentialVersion = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // UC-03 Rule: Exactly one internal role per admin account.
    // The DB schema V002 uses a many-to-many join table (internal_admin_roles).
    // We map it using @ManyToOne + @JoinTable to simplify the mapping while matching the schema.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinTable(
        name = "internal_admin_roles",
        joinColumns = @JoinColumn(name = "admin_account_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Role role;
}
