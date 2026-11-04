package com.manabihub.learning.service;

import com.manabihub.learning.entity.Enrollment;

public interface EnrollmentProgressResetService {

    /**
     * Starts a refunded enrollment again from zero after a successful repurchase.
     * The caller must hold a write lock on the enrollment and an active transaction.
     */
    void resetForRepurchase(Enrollment enrollment);
}
