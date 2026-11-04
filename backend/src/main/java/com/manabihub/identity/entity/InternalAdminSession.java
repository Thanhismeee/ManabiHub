package com.manabihub.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "internal_admin_sessions")
@Getter
@Setter
public class InternalAdminSession {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "admin_account_id", nullable = false)
    private UUID adminAccountId;

    @Column(name = "csrf_token_hash", nullable = false, length = 64)
    private String csrfTokenHash;

    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;

    @Column(name = "remember_me", nullable = false)
    private boolean rememberMe;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "idle_expires_at", nullable = false)
    private Instant idleExpiresAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
