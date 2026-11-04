package com.manabihub.moderation.repository;

import com.manabihub.moderation.entity.ViolationReport;
import com.manabihub.moderation.enums.ViolationReportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ViolationReportRepository extends JpaRepository<ViolationReport, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ViolationReport v WHERE v.id = :id")
    Optional<ViolationReport> findByIdLocked(@Param("id") UUID id);

    Page<ViolationReport> findByStatus(ViolationReportStatus status, Pageable pageable);

    long countByTargetTypeIgnoreCaseAndTargetIdAndStatus(
            String targetType,
            UUID targetId,
            ViolationReportStatus status
    );
}
