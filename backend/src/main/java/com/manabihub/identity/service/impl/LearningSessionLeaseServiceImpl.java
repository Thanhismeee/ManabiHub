package com.manabihub.identity.service.impl;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.ActiveLearningSession;
import com.manabihub.identity.repository.ActiveLearningSessionRepository;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.service.LearningSessionLeaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LearningSessionLeaseServiceImpl implements LearningSessionLeaseService {

    private final ActiveLearningSessionRepository learningSessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final AppUserRepository appUserRepository;

    private static final int LEASE_TTL_SECONDS = 120;

    @Override
    @Transactional
    public void acquireLease(UUID userId, UUID publicSessionId, UUID courseId) {
        // A lease may not exist yet. Lock the parent row to serialize first acquisition too.
        appUserRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(MessageCodes.COMMON_NOT_FOUND, "User not found", HttpStatus.NOT_FOUND));
        Instant now = Instant.now();
        Optional<ActiveLearningSession> currentLeaseOpt = learningSessionRepository.findByIdForUpdate(userId);

        if (currentLeaseOpt.isPresent()) {
            ActiveLearningSession currentLease = currentLeaseOpt.get();
            
            // If same session owns lease, refresh it
            if (currentLease.getPublicSessionId().equals(publicSessionId)) {
                currentLease.setCourseId(courseId);
                currentLease.setLastHeartbeatAt(now);
                currentLease.setExpiresAt(now.plus(LEASE_TTL_SECONDS, ChronoUnit.SECONDS));
                learningSessionRepository.save(currentLease);
                return;
            }

            // If another session owns non-expired lease, reject
            if (now.isBefore(currentLease.getExpiresAt())) {
                auditLogRepository.saveAndFlush(AuditLog.builder()
                        .actorType("USER")
                        .actorUserId(userId)
                        .action("LEARNING_SESSION_CONFLICT")
                        .targetType("LEARNING_LEASE")
                        .build());

                throw new BusinessException(
                        MessageCodes.ACCOUNT_IN_USE_ELSEWHERE,
                        "Your account is being used to learn on another device.",
                        HttpStatus.CONFLICT
                );
            }

            // Another session's lease expired, we can steal it
            currentLease.setPublicSessionId(publicSessionId);
            currentLease.setCourseId(courseId);
            currentLease.setAcquiredAt(now);
            currentLease.setLastHeartbeatAt(now);
            currentLease.setExpiresAt(now.plus(LEASE_TTL_SECONDS, ChronoUnit.SECONDS));
            learningSessionRepository.save(currentLease);
            return;
        }

        // No lease exists, grant new lease
        ActiveLearningSession newLease = new ActiveLearningSession();
        newLease.setUserId(userId);
        newLease.setPublicSessionId(publicSessionId);
        newLease.setCourseId(courseId);
        newLease.setAcquiredAt(now);
        newLease.setLastHeartbeatAt(now);
        newLease.setExpiresAt(now.plus(LEASE_TTL_SECONDS, ChronoUnit.SECONDS));
        learningSessionRepository.save(newLease);
    }

    @Override
    @Transactional
    public void releaseLease(UUID userId, UUID publicSessionId) {
        Optional<ActiveLearningSession> leaseOpt = learningSessionRepository.findByIdForUpdate(userId);
        if (leaseOpt.isPresent() && leaseOpt.get().getPublicSessionId().equals(publicSessionId)) {
            learningSessionRepository.delete(leaseOpt.get());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean ownsLease(UUID userId, UUID publicSessionId) {
        Optional<ActiveLearningSession> leaseOpt = learningSessionRepository.findById(userId);
        if (leaseOpt.isEmpty()) {
            return false;
        }
        ActiveLearningSession lease = leaseOpt.get();
        return lease.getPublicSessionId().equals(publicSessionId) && Instant.now().isBefore(lease.getExpiresAt());
    }
}
