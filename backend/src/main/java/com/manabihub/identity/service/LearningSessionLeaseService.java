package com.manabihub.identity.service;

import java.util.UUID;

public interface LearningSessionLeaseService {

    void acquireLease(UUID userId, UUID publicSessionId, UUID courseId);

    void releaseLease(UUID userId, UUID publicSessionId);
    
    boolean ownsLease(UUID userId, UUID publicSessionId);
}
