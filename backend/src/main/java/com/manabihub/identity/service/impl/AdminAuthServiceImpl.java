package com.manabihub.identity.service.impl;

import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.audit.service.SecurityAuditService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.dto.request.LoginRequest;
import com.manabihub.identity.dto.response.AdminProfileResponse;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.enums.AccountStatus;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import com.manabihub.identity.service.AdminLoginProtection;
import com.manabihub.identity.service.AdminSessionBundle;
import com.manabihub.identity.service.AdminAuthService;
import com.manabihub.identity.service.InternalAdminSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
// [CODE NOTE - UC-03]: Class xử lý nghiệp vụ Đăng nhập của Admin bằng Username/Password (không dùng Google OAuth).
// Đáp ứng tiêu chí: "System Admin, Course Manager, and Finance Manager can log in through the same Admin Portal."
// Tại đây có triển khai quy tắc chống Brute-force (BR-AUTH-09) và ghi log kiểm toán (BR-AUD-01).
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final String OVERSIZED_PASSWORD_SENTINEL =
            "invalid-password-length";

    private final InternalAdminAccountRepository adminAccountRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminLoginProtection loginProtection;
    private final InternalAdminSessionService sessionService;
    private final SecurityAuditService securityAuditService;

    @Override
    @Transactional
    // [CODE NOTE - UC-03]: Hàm login() xử lý xác thực email/password.
    // - Đáp ứng tiêu chí: "Invalid/locked login is handled safely with generic error response." (Báo lỗi chung MSG-AUTH-007).
    // - Đáp ứng tiêu chí: "Locked/disabled internal account cannot log in." (Kiểm tra AccountStatus và In-memory lock).
    public AdminSessionBundle login(
            LoginRequest request,
            String ipAddress,
            String userAgent
    ) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        loginProtection.check(email, ipAddress);

        InternalAdminAccount account = adminAccountRepository.findByEmail(email)
                .orElse(null);

        // General error for security (EX-03) - Trả về lỗi chung, không tiết lộ tài khoản có tồn tại hay không
        if (account == null) {
            matchesPassword(
                    request.getPassword(),
                    DUMMY_PASSWORD_HASH
            );
            loginProtection.recordFailure(email, ipAddress);
            throw new BusinessException(MessageCodes.MSG_AUTH_007, "Admin login failed");
        }

        boolean passwordMatches = matchesPassword(
                request.getPassword(),
                account.getPasswordHash()
        );
        if (account.getAccountStatus() != AccountStatus.ACTIVE
                || account.getRole() == null) {
            loginProtection.recordFailure(email, ipAddress);
            securityAuditService.logInternalAdminAuthenticationFailure(
                    account.getId(),
                    account.getRole() == null ? null : account.getRole().getCode().name(),
                    "DISABLED_ACCOUNT",
                    ipAddress,
                    userAgent
            );
            throw new BusinessException(MessageCodes.MSG_AUTH_007, "Admin login failed");
        }

        if (!passwordMatches) {
            loginProtection.recordFailure(email, ipAddress);
            securityAuditService.logInternalAdminAuthenticationFailure(
                    account.getId(),
                    account.getRole().getCode().name(),
                    "INVALID_PASSWORD",
                    ipAddress,
                    userAgent
            );
            throw new BusinessException(MessageCodes.MSG_AUTH_007, "Admin login failed");
        }

        // Success -> reset attempts
        loginProtection.reset(email, ipAddress);

        // Update last login
        account.setLastLoginAt(Instant.now());
        adminAccountRepository.save(account);

        // Audit Log
        logAudit(account.getId(), account.getRole().getCode().name(), "LOGIN_SUCCESS", "AUTH", ipAddress, userAgent);

        return sessionService.create(account, request.isRememberMe(), userAgent);
    }

    // [CODE NOTE - UC-03]: Hàm getMe() trả về thông tin Profile bao gồm cả Role (SYSTEM_ADMIN, COURSE_MANAGER...).
    // Đáp ứng tiêu chí: "Backend returns permission/role information for menu routing".
    @Override
    @Transactional(readOnly = true)
    public AdminProfileResponse getMe(UUID adminId) {
        InternalAdminAccount account = adminAccountRepository.findById(java.util.Objects.requireNonNull(adminId))
                .orElseThrow(() -> new BusinessException(MessageCodes.COMMON_NOT_FOUND, "Account not found"));

        return AdminProfileResponse.builder()
                .id(account.getId())
                .email(account.getEmail())
                .fullName(account.getFullName())
                .role(account.getRole().getCode().name())
                .build();
    }

    private void logAudit(UUID adminId, String role, String action, String targetType, String ipAddress, String userAgent) {
        AuditLog auditLog = AuditLog.builder()
                .actorType("INTERNAL_ADMIN")
                .actorAdminId(adminId)
                .actorRoleCode(role)
                .action(action)
                .targetType(targetType)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        auditLogRepository.save(java.util.Objects.requireNonNull(auditLog));
    }

    private boolean matchesPassword(String rawPassword, String passwordHash) {
        boolean acceptableLength = rawPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
        boolean matches = passwordEncoder.matches(
                acceptableLength ? rawPassword : OVERSIZED_PASSWORD_SENTINEL,
                passwordHash
        );
        return acceptableLength && matches;
    }
}
