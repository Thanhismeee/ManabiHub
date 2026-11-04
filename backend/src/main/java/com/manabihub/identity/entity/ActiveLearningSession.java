package com.manabihub.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "active_learning_sessions")
@Getter
@Setter
public class ActiveLearningSession {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "public_session_id", nullable = false)
    private UUID publicSessionId;

    @Column(name = "course_id")
    private UUID courseId;

    @CreationTimestamp
    @Column(name = "acquired_at", nullable = false, updatable = false)
    private Instant acquiredAt;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
