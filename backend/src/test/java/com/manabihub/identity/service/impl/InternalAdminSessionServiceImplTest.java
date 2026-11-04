package com.manabihub.identity.service.impl;

import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.entity.InternalAdminRefreshToken;
import com.manabihub.identity.entity.InternalAdminSession;
import com.manabihub.identity.entity.Role;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.enums.InternalAdminRefreshTokenStatus;
import com.manabihub.identity.enums.RoleCode;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.repository.InternalAdminRefreshTokenRepository;
import com.manabihub.identity.repository.InternalAdminSessionRepository;
import com.manabihub.identity.service.AdminSessionBundle;
import com.manabihub.identity.service.SecureTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalAdminSessionServiceImplTest {

    @Mock private InternalAdminSessionRepository sessionRepository;
    @Mock private InternalAdminRefreshTokenRepository refreshTokenRepository;
    @Mock private InternalAdminAccountRepository accountRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private SecureTokenService tokenService;
    @Mock private JwtEncoder jwtEncoder;

    private InternalAdminSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InternalAdminSessionServiceImpl(
                sessionRepository,
                refreshTokenRepository,
                accountRepository,
                auditLogRepository,
                tokenService,
                jwtEncoder
        );
        ReflectionTestUtils.setField(service, "accessTokenMinutes", 15L);
        ReflectionTestUtils.setField(service, "sessionHours", 12L);
        ReflectionTestUtils.setField(service, "rememberedSessionDays", 30L);
        ReflectionTestUtils.setField(service, "rememberedIdleDays", 7L);
        Jwt encodedJwt = Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .claim("sub", "admin")
                .build();
        lenient().when(jwtEncoder.encode(any())).thenReturn(encodedJwt);
    }

    @Test
    void createPersistsOnlyHashesAndReturnsRawCredentialsOnce() {
        InternalAdminAccount account = account();
        when(tokenService.randomToken())
                .thenReturn("raw-refresh", "raw-csrf");
        when(tokenService.hash("raw-refresh")).thenReturn("refresh-hash");
        when(tokenService.hash("raw-csrf")).thenReturn("csrf-hash");
        when(sessionRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> {
                    InternalAdminSession session = invocation.getArgument(0);
                    session.setId(UUID.randomUUID());
                    return session;
                });
        when(refreshTokenRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminSessionBundle bundle = service.create(account, true, "JUnit");

        assertEquals("access-token", bundle.accessToken());
        assertEquals("raw-refresh", bundle.refreshToken());
        assertEquals("raw-csrf", bundle.csrfToken());
        assertEquals(true, bundle.remembered());
        verify(sessionRepository).saveAndFlush(
                org.mockito.ArgumentMatchers.argThat(session ->
                        "csrf-hash".equals(session.getCsrfTokenHash())
                                && session.isRememberMe()
                )
        );
        verify(refreshTokenRepository).saveAndFlush(
                org.mockito.ArgumentMatchers.argThat(token ->
                        "refresh-hash".equals(token.getTokenHash())
                )
        );
    }

    @Test
    void refreshRotatesTokenAndUpdatesIdleWindow() {
        InternalAdminAccount account = account();
        InternalAdminSession session = activeSession(account);
        InternalAdminRefreshToken currentToken = activeRefreshToken(session);
        when(tokenService.hash("old-refresh")).thenReturn("old-hash");
        when(tokenService.matches("old-csrf", "csrf-hash")).thenReturn(true);
        when(tokenService.randomToken()).thenReturn("new-refresh", "new-csrf");
        when(tokenService.hash("new-refresh")).thenReturn("new-refresh-hash");
        when(tokenService.hash("new-csrf")).thenReturn("new-csrf-hash");
        when(refreshTokenRepository.findByTokenHashForUpdate("old-hash"))
                .thenReturn(Optional.of(currentToken));
        when(sessionRepository.findByIdForUpdate(session.getId()))
                .thenReturn(Optional.of(session));
        when(accountRepository.findByIdForRoleUpdate(account.getId()))
                .thenReturn(Optional.of(account));
        when(refreshTokenRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> {
                    InternalAdminRefreshToken token = invocation.getArgument(0);
                    if (token.getId() == null) {
                        token.setId(UUID.randomUUID());
                    }
                    return token;
                });
        when(sessionRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminSessionBundle bundle = service.refresh(
                "old-refresh",
                "old-csrf",
                "JUnit"
        );

        assertEquals("new-refresh", bundle.refreshToken());
        assertEquals("new-csrf", bundle.csrfToken());
        assertEquals(InternalAdminRefreshTokenStatus.ROTATED, currentToken.getStatus());
        assertNotNull(currentToken.getReplacedBy());
        assertEquals("new-csrf-hash", session.getCsrfTokenHash());
    }

    @Test
    void replayOfRotatedTokenRevokesTheWholeSessionFamily() {
        InternalAdminAccount account = account();
        InternalAdminSession session = activeSession(account);
        InternalAdminRefreshToken replayedToken = activeRefreshToken(session);
        replayedToken.setStatus(InternalAdminRefreshTokenStatus.ROTATED);
        when(tokenService.hash("replayed")).thenReturn("replayed-hash");
        when(refreshTokenRepository.findByTokenHashForUpdate("replayed-hash"))
                .thenReturn(Optional.of(replayedToken));
        when(sessionRepository.findByIdForUpdate(session.getId()))
                .thenReturn(Optional.of(session));

        assertThrows(
                BusinessException.class,
                () -> service.refresh("replayed", "csrf", "JUnit")
        );

        assertNotNull(session.getRevokedAt());
        verify(refreshTokenRepository).revokeActiveForSession(
                eq(session.getId()),
                eq(InternalAdminRefreshTokenStatus.REVOKED),
                any()
        );
        verify(auditLogRepository).saveAndFlush(any());
        verify(accountRepository, never()).findByIdForRoleUpdate(any());
    }


    @Test
    void refreshOfAlreadyRevokedSessionKeepsTheOriginalRevocationTime() {
        InternalAdminAccount account = account();
        InternalAdminSession session = activeSession(account);
        Instant originalRevocation = Instant.parse("2026-09-21T04:18:05.314696Z");
        session.setRevokedAt(originalRevocation);
        InternalAdminRefreshToken token = activeRefreshToken(session);
        when(tokenService.hash("old-refresh")).thenReturn("old-hash");
        when(tokenService.matches("old-csrf", "csrf-hash")).thenReturn(true);
        when(refreshTokenRepository.findByTokenHashForUpdate("old-hash"))
                .thenReturn(Optional.of(token));
        when(sessionRepository.findByIdForUpdate(session.getId()))
                .thenReturn(Optional.of(session));

        assertThrows(
                BusinessException.class,
                () -> service.refresh("old-refresh", "old-csrf", "JUnit")
        );

        assertEquals(originalRevocation, session.getRevokedAt());
        verify(sessionRepository, never()).saveAndFlush(any());
        verify(refreshTokenRepository).revokeActiveForSession(
                eq(session.getId()),
                eq(InternalAdminRefreshTokenStatus.REVOKED),
                any()
        );
    }

    private InternalAdminAccount account() {
        Role role = new Role();
        role.setCode(RoleCode.SYSTEM_ADMIN);
        InternalAdminAccount account = new InternalAdminAccount();
        account.setId(UUID.randomUUID());
        account.setEmail("admin@example.com");
        account.setFullName("System Admin");
        account.setPasswordHash("hash");
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setCredentialVersion(2);
        account.setRole(role);
        return account;
    }

    private InternalAdminSession activeSession(InternalAdminAccount account) {
        InternalAdminSession session = new InternalAdminSession();
        session.setId(UUID.randomUUID());
        session.setAdminAccountId(account.getId());
        session.setCredentialVersion(account.getCredentialVersion());
        session.setCsrfTokenHash("csrf-hash");
        session.setRememberMe(true);
        session.setExpiresAt(Instant.now().plusSeconds(86_400));
        session.setIdleExpiresAt(Instant.now().plusSeconds(43_200));
        session.setLastUsedAt(Instant.now());
        return session;
    }

    private InternalAdminRefreshToken activeRefreshToken(
            InternalAdminSession session
    ) {
        InternalAdminRefreshToken token = new InternalAdminRefreshToken();
        token.setId(UUID.randomUUID());
        token.setSessionId(session.getId());
        token.setTokenHash("old-hash");
        token.setStatus(InternalAdminRefreshTokenStatus.ACTIVE);
        token.setExpiresAt(session.getExpiresAt());
        return token;
    }
}
