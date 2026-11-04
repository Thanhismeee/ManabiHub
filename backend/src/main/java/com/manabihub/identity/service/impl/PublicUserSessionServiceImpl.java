package com.manabihub.identity.service.impl;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.PublicUserDevice;
import com.manabihub.identity.entity.PublicUserSession;
import com.manabihub.identity.repository.ActiveLearningSessionRepository;
import com.manabihub.identity.repository.PublicUserDeviceRepository;
import com.manabihub.identity.repository.PublicUserSessionRepository;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.service.PublicUserSessionService;
import com.manabihub.identity.service.SecureTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicUserSessionServiceImpl implements PublicUserSessionService {

    private final PublicUserDeviceRepository deviceRepository;
    private final PublicUserSessionRepository sessionRepository;
    private final ActiveLearningSessionRepository learningSessionRepository;
    private final SecureTokenService tokenService;
    private final AuditLogRepository auditLogRepository;
    private final AppUserRepository appUserRepository;

    private static final int MAX_TRUSTED_DEVICES = 2;
    private static final int SESSION_HOURS = 24;

    @Override
    @Transactional
    public PublicUserSession createSession(UUID userId, String deviceKey, String userAgent, String displayName) {
        String deviceHash = tokenService.hash(deviceKey);
        Instant now = Instant.now();

        // Lock user row to prevent race condition when counting and creating devices
        appUserRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(MessageCodes.COMMON_NOT_FOUND, "User not found", HttpStatus.NOT_FOUND));

        Optional<PublicUserDevice> existingDevice = deviceRepository.findByUserIdAndDeviceKeyHashAndRevokedAtIsNull(userId, deviceHash);
        PublicUserDevice device;

        if (existingDevice.isPresent()) {
            device = existingDevice.get();
            device.setLastSeenAt(now);
            device.setUserAgent(safeString(userAgent));
            device = deviceRepository.save(device);
        } else {
            long activeDevices = deviceRepository.countActiveDevicesByUserId(userId);
            if (activeDevices >= MAX_TRUSTED_DEVICES) {
                logSecurityEvent(userId, "PUBLIC_DEVICE_LIMIT_REJECTED", null);
                throw new BusinessException(
                        MessageCodes.PUBLIC_DEVICE_LIMIT_REACHED,
                        "Maximum number of devices reached. Please revoke an existing device.",
                        HttpStatus.CONFLICT
                );
            }

            device = new PublicUserDevice();
            device.setId(UUID.randomUUID());
            device.setUserId(userId);
            device.setDeviceKeyHash(deviceHash);
            device.setDisplayName(displayName != null ? displayName : "Unknown Device");
            device.setUserAgent(safeString(userAgent));
            device.setLastSeenAt(now);
            device = deviceRepository.save(device);

            logSecurityEvent(userId, "PUBLIC_DEVICE_REGISTERED", device.getId());
        }

        PublicUserSession session = new PublicUserSession();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setDeviceId(device.getId());
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plus(SESSION_HOURS, ChronoUnit.HOURS));
        session = sessionRepository.save(session);

        logSecurityEvent(userId, "PUBLIC_SESSION_CREATED", session.getId());
        return session;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSessionValid(UUID sessionId, UUID userId) {
        Optional<PublicUserSession> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            return false;
        }
        PublicUserSession session = sessionOpt.get();
        if (!session.getUserId().equals(userId)) {
            return false;
        }
        if (session.getRevokedAt() != null) {
            return false;
        }
        if (session.getExpiresAt().isBefore(Instant.now())) {
            return false;
        }
        Optional<PublicUserDevice> deviceOpt = deviceRepository.findById(session.getDeviceId());
        if (deviceOpt.isEmpty() || deviceOpt.get().getRevokedAt() != null) {
            return false;
        }
        return true;
    }

    @Override
    @Transactional
    public void revokeDevice(UUID userId, UUID deviceId) {
        Optional<PublicUserDevice> deviceOpt = deviceRepository.findById(deviceId);
        if (deviceOpt.isEmpty() || !deviceOpt.get().getUserId().equals(userId)) {
            throw new BusinessException(MessageCodes.COMMON_NOT_FOUND, "Device not found", HttpStatus.NOT_FOUND);
        }
        PublicUserDevice device = deviceOpt.get();
        if (device.getRevokedAt() == null) {
            device.setRevokedAt(Instant.now());
            deviceRepository.save(device);
            
            sessionRepository.revokeAllByDeviceId(deviceId, Instant.now());
            learningSessionRepository.deleteByDeviceId(deviceId);

            logSecurityEvent(userId, "PUBLIC_DEVICE_REVOKED", deviceId);
        }
    }

    @Override
    @Transactional
    public void revokeSession(UUID sessionId) {
        Optional<PublicUserSession> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isPresent()) {
            PublicUserSession session = sessionOpt.get();
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(Instant.now());
                sessionRepository.save(session);
                
                learningSessionRepository.deleteByPublicSessionId(sessionId);

                logSecurityEvent(session.getUserId(), "PUBLIC_SESSION_REVOKED", sessionId);
            }
        }
    }

    private String safeString(String value) {
        return value == null ? null : value.substring(0, Math.min(value.length(), 1000));
    }

    private void logSecurityEvent(UUID userId, String action, UUID targetId) {
        auditLogRepository.saveAndFlush(AuditLog.builder()
                .actorType("USER")
                .actorUserId(userId)
                .action(action)
                .targetType(targetId != null ? "DEVICE_OR_SESSION" : "USER")
                .targetId(targetId != null ? targetId : userId)
                .build());
    }
}
