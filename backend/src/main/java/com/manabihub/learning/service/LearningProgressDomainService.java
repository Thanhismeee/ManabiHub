package com.manabihub.learning.service;

import java.util.UUID;

/**
 * Authoritative service for calculating a student's learning progress.
 */
public interface LearningProgressDomainService {

    /**
     * Calculates the progress for a specific enrollment.
     *
     * @param courseId the ID of the course
     * @param enrollmentId the ID of the enrollment
     * @return a record containing the completed count, total count, and progress percentage
     */
    ProgressResult calculateProgress(UUID courseId, UUID enrollmentId);

    record ProgressResult(int completed, int total, double percent) {
    }
}
