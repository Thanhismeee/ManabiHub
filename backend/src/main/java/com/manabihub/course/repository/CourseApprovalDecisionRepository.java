package com.manabihub.course.repository;

import com.manabihub.course.entity.CourseApprovalDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourseApprovalDecisionRepository extends JpaRepository<CourseApprovalDecision, UUID> {
}
