package com.manabihub.violation.repository;

import com.manabihub.violation.entity.ViolationReport;
import com.manabihub.violation.enums.ViolationTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository("uc20ViolationReportRepository")
public interface ViolationReportRepository extends JpaRepository<ViolationReport, UUID> {

    @org.springframework.data.jpa.repository.Query("SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END FROM Uc20ViolationReport v " +
            "WHERE v.reporter.id = :reporterId AND v.targetType = :targetType AND v.targetId = :targetId AND v.createdAt >= :after")
    boolean isDuplicateReport(
            @org.springframework.data.repository.query.Param("reporterId") UUID reporterId, 
            @org.springframework.data.repository.query.Param("targetType") ViolationTargetType targetType, 
            @org.springframework.data.repository.query.Param("targetId") UUID targetId, 
            @org.springframework.data.repository.query.Param("after") Instant after);
}
