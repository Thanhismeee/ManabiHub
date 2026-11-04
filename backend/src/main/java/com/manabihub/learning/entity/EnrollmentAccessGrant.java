package com.manabihub.learning.entity;

import com.manabihub.learning.enums.EnrollmentAccessSource;
import com.manabihub.learning.enums.EnrollmentStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/** One independently revocable source of access; learning progress belongs to Enrollment. */
@Entity @Table(name = "enrollment_access_grants")
@Getter @Setter @NoArgsConstructor
public class EnrollmentAccessGrant {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;
    @Enumerated(EnumType.STRING) @Column(name = "access_source", nullable = false)
    private EnrollmentAccessSource accessSource;
    @Column(name = "source_reference_id", nullable = false) private UUID sourceReferenceId;
    @Column(name = "cohort_id") private UUID cohortId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private EnrollmentStatus status;
    @Column(name = "granted_at", nullable = false) private Instant grantedAt;
    @Column(name = "expires_at") private Instant expiresAt;
    public boolean activeAt(Instant now) {
        return status == EnrollmentStatus.ACTIVE && (expiresAt == null || expiresAt.isAfter(now));
    }
}
