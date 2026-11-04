package com.manabihub.identity.service.impl;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.entity.InternalAdminRefreshToken;
import com.manabihub.identity.entity.InternalAdminSession;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.enums.InternalAdminRefreshTokenStatus;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.repository.InternalAdminRefreshTokenRepository;
import com.manabihub.identity.repository.InternalAdminSessionRepository;
import com.manabihub.identity.service.AdminSessionBundle;
import com.manabihub.identity.service.InternalAdminSessionService;
import com.manabihub.identity.service.SecureTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalAdminSessionServiceImpl implements InternalAdminSessionService {

    private final InternalAdminSessionRepository sessionRepository;
    private final InternalAdminRefreshTokenRepository refreshTokenRepository;
    private final InternalAdminAccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final SecureTokenService tokenService;
    private final JwtEncoder jwtEncoder;

    @Value("${manabihub.admin-auth.access-token-minutes:15}")
    private long accessTokenMinutes;

    @Value("${manabihub.admin-auth.session-hours:12}")
    private long sessionHours;

    @Value("${manabihub.admin-auth.remember-session-days:30}")
    private long rememberedSessionDays;

    @Value("${manabihub.admin-auth.remember-idle-days:7}")
    private long rememberedIdleDays;

    @Override
    @Transactional
    public AdminSessionBundle create(
            InternalAdminAccount account,
            boolean rememberMe,
            String userAgent
    ) {
        Instant now = Instant.now();
        InternalAdminSession session = new InternalAdminSession();
        String refreshToken = tokenService.randomToken();
        String csrfToken = tokenService.randomToken();
        session.setAdminAccountId(account.getId());
        session.setCsrfTokenHash(tokenService.hash(csrfToken));
        session.setCredentialVersion(account.getCredentialVersion());
        session.setRememberMe(rememberMe);
        session.setExpiresAt(
                rememberMe
                        ? now.plus(rememberedSessionDays, ChronoUnit.DAYS)
                        : now.plus(sessionHours, ChronoUnit.HOURS)
        );
        session.setIdleExpiresAt(
                rememberMe
                        ? now.plus(rememberedIdleDays, ChronoUnit.DAYS)
                        : session.getExpiresAt()
        );
        session.setLastUsedAt(now);
        session.setUserAgent(safeUserAgent(userAgent));
        InternalAdminSession saved = sessionRepository.saveAndFlush(session);
        InternalAdminRefreshToken refreshTokenEntity = new InternalAdminRefreshToken();
        refreshTokenEntity.setSessionId(saved.getId());
        refreshTokenEntity.setTokenHash(tokenService.hash(refreshToken));
        refreshTokenEntity.setExpiresAt(saved.getExpiresAt());
        refreshTokenRepository.saveAndFlush(refreshTokenEntity);
        return bundle(account, saved, refreshToken, csrfToken, now);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public AdminSessionBundle refresh(
            String refreshToken,
            String csrfToken,
            String userAgent
    ) {
        String refreshHash = safeHash(refreshToken);
        InternalAdminRefreshToken currentRefreshToken = refreshTokenRepository
                .findByTokenHashForUpdate(refreshHash)
                .orElseThrow(this::invalidSession);
        InternalAdminSession session = sessionRepository
                .findByIdForUpdate(currentRefreshToken.getSessionId())
                .orElseThrow(this::invalidSession);
        Instant now = Instant.now();

        if (currentRefreshToken.getStatus()
                != InternalAdminRefreshTokenStatus.ACTIVE) {
            revokeSessionFamily(session, now);
            logSecurityEvent(session.getAdminAccountId(), "ADMIN_REFRESH_TOKEN_REUSE", userAgent);
            throw invalidSession();
        }
        if (!tokenService.matches(csrfToken, session.getCsrfTokenHash())) {
            throw invalidSession();
        }
        if (session.getRevokedAt() != null
                || !now.isBefore(session.getExpiresAt())
                || !now.isBefore(session.getIdleExpiresAt())
                || !now.isBefore(currentRefreshToken.getExpiresAt())) {
            revokeSessionFamily(session, now);
            throw invalidSession();
        }

        InternalAdminAccount account = accountRepository
                .findByIdForRoleUpdate(session.getAdminAccountId())
                .orElseThrow(this::invalidSession);
        if (account.getAccountStatus() != AccountStatus.ACTIVE
                || account.getRole() == null
                || account.getCredentialVersion() != session.getCredentialVersion()) {
            revokeSessionFamily(session, now);
            throw invalidSession();
        }

        String nextRefreshToken = tokenService.randomToken();
        String nextCsrfToken = tokenService.randomToken();
        currentRefreshToken.setStatus(InternalAdminRefreshTokenStatus.ROTATED);
        currentRefreshToken.setUsedAt(now);
        refreshTokenRepository.saveAndFlush(currentRefreshToken);

        InternalAdminRefreshToken nextRefreshTokenEntity =
                new InternalAdminRefreshToken();
        nextRefreshTokenEntity.setSessionId(session.getId());
        nextRefreshTokenEntity.setTokenHash(tokenService.hash(nextRefreshToken));
        nextRefreshTokenEntity.setExpiresAt(session.getExpiresAt());
        InternalAdminRefreshToken savedRefreshToken =
                refreshTokenRepository.saveAndFlush(nextRefreshTokenEntity);
        currentRefreshToken.setReplacedBy(savedRefreshToken.getId());
        refreshTokenRepository.save(currentRefreshToken);

        session.setCsrfTokenHash(tokenService.hash(nextCsrfToken));
        session.setLastUsedAt(now);
        if (session.isRememberMe()) {
            Instant idleExpiry = now.plus(rememberedIdleDays, ChronoUnit.DAYS);
            session.setIdleExpiresAt(
                    idleExpiry.isBefore(session.getExpiresAt())
                            ? idleExpiry
                            : session.getExpiresAt()
            );
        }
        session.setUserAgent(safeUserAgent(userAgent));
        InternalAdminSession saved = sessionRepository.saveAndFlush(session);
        return bundle(account, saved, nextRefreshToken, nextCsrfToken, now);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public void logout(
            String refreshToken,
            String csrfToken,
            String ipAddress,
            String userAgent
    ) {
        String refreshHash = safeHash(refreshToken);
        InternalAdminRefreshToken currentRefreshToken = refreshTokenRepository
                .findByTokenHashForUpdate(refreshHash)
                .orElseThrow(this::invalidSession);
        InternalAdminSession session = sessionRepository
                .findByIdForUpdate(currentRefreshToken.getSessionId())
                .orElseThrow(this::invalidSession);
        if (!tokenService.matches(csrfToken, session.getCsrfTokenHash())) {
            throw invalidSession();
        }
        if (session.getRevokedAt() == null) {
            revokeSessionFamily(session, Instant.now());
            auditLogRepository.save(AuditLog.builder()
                    .actorType("INTERNAL_ADMIN")
                    .actorAdminId(session.getAdminAccountId())
                    .action("LOGOUT")
                    .targetType("ADMIN_SESSION")
                    .targetId(session.getId())
                    .ipAddress(safeMetadata(ipAddress))
                    .userAgent(safeMetadata(userAgent))
                    .build());
        }
    }

    @Override
    @Transactional
    public void revokeAll(UUID adminAccountId) {
        Instant now = Instant.now();
        sessionRepository.revokeAllForAccount(adminAccountId, now);
        refreshTokenRepository.revokeActiveForAccount(
                adminAccountId,
                InternalAdminRefreshTokenStatus.REVOKED,
                now
        );
    }

    private AdminSessionBundle bundle(
            InternalAdminAccount account,
            InternalAdminSession session,
            String refreshToken,
            String csrfToken,
            Instant issuedAt
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("manabihub-admin")
                .audience(List.of("admin-api"))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(accessTokenMinutes, ChronoUnit.MINUTES))
                .id(UUID.randomUUID().toString())
                .subject(account.getId().toString())
                .claim("email", account.getEmail())
                .claim("role", account.getRole().getCode().name())
                .claim("type", "ADMIN_ACCESS")
                .claim("sid", session.getId().toString())
                .claim("cv", account.getCredentialVersion())
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims
        )).getTokenValue();
        return new AdminSessionBundle(
                accessToken,
                refreshToken,
                csrfToken,
                session.isRememberMe(),
                session.getExpiresAt()
        );
    }

    private String safeHash(String token) {
        try {
            return tokenService.hash(token);
        } catch (IllegalArgumentException exception) {
            throw invalidSession();
        }
    }

    private BusinessException invalidSession() {
        return new BusinessException(
                MessageCodes.ADMIN_SESSION_INVALID,
                "Administrator session is invalid or expired",
                HttpStatus.UNAUTHORIZED
        );
    }

    private void logSecurityEvent(UUID adminId, String action, String userAgent) {
        auditLogRepository.saveAndFlush(AuditLog.builder()
                .actorType("INTERNAL_ADMIN")
                .actorAdminId(adminId)
                .action(action)
                .targetType("ADMIN_SESSION")
                .metadata(Map.of("reason", "ROTATED_TOKEN_REUSED"))
                .userAgent(safeMetadata(userAgent))
                .build());
    }

    private void revokeSessionFamily(
            InternalAdminSession session,
            Instant revokedAt
    ) {
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(revokedAt);
            sessionRepository.saveAndFlush(session);
        }
        refreshTokenRepository.revokeActiveForSession(
                session.getId(),
                InternalAdminRefreshTokenStatus.REVOKED,
                revokedAt
        );
    }

    private String safeUserAgent(String value) {
        return value == null ? null : value.substring(0, Math.min(value.length(), 500));
    }

    private String safeMetadata(String value) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), 500));
    }
}
