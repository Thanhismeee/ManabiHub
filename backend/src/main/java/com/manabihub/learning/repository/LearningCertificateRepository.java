package com.manabihub.learning.repository;

import com.manabihub.learning.entity.LearningCertificate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LearningCertificateRepository extends JpaRepository<LearningCertificate, UUID> {

    @EntityGraph(attributePaths = {"enrollment", "enrollment.course", "enrollment.student"})
    Optional<LearningCertificate> findByEnrollmentId(UUID enrollmentId);
}
