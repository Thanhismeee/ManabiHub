package com.manabihub.learning.repository;

import com.manabihub.learning.entity.EnrollmentAccessGrant;
import com.manabihub.learning.enums.EnrollmentAccessSource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface EnrollmentAccessGrantRepository extends JpaRepository<EnrollmentAccessGrant, UUID> {
    List<EnrollmentAccessGrant> findByEnrollment_Id(UUID enrollmentId);
    Optional<EnrollmentAccessGrant> findByAccessSourceAndSourceReferenceId(EnrollmentAccessSource source, UUID reference);
}
