package com.manabihub.identity.controller;

import com.manabihub.common.response.ApiResponse;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.identity.dto.request.ChangeAdminPasswordRequest;
import com.manabihub.identity.dto.request.ForgotAdminPasswordRequest;
import com.manabihub.identity.dto.request.LoginRequest;
import com.manabihub.identity.dto.request.ResetAdminPasswordRequest;
import com.manabihub.identity.dto.request.SetupAdminPasswordRequest;
import com.manabihub.identity.dto.response.AdminProfileResponse;
import com.manabihub.identity.dto.response.LoginResponse;
import com.manabihub.identity.service.AdminPasswordResetService;
import com.manabihub.identity.service.AdminRefreshCookieService;
import com.manabihub.identity.service.AdminSessionBundle;
import com.manabihub.identity.service.AdminAuthService;
import com.manabihub.identity.service.InternalAdminInvitationService;
import com.manabihub.identity.service.InternalAdminSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@Tag(name = "Admin Authentication", description = "Internal Admin Portal Authentication APIs")
// [CODE NOTE - UC-03]: Controller xử lý API cho Admin Portal.
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final InternalAdminInvitationService invitationService;
    private final InternalAdminSessionService sessionService;
    private final AdminPasswordResetService passwordResetService;
    private final AdminRefreshCookieService refreshCookieService;

    @Operation(summary = "Login to Admin Portal", description = "Authenticates internal admins via email/password and returns a JWT.")
    // [CODE NOTE - UC-03]: API Đăng nhập dành riêng cho Admin Portal (Username/Password), độc lập với App User (Google OAuth).
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        AdminSessionBundle session = adminAuthService.login(
                request,
                ipAddress,
                userAgent
        );
        writeRefreshCookie(httpResponse, session);
        return ApiResponse.success(toLoginResponse(session));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(
            @CookieValue(
                    name = AdminRefreshCookieService.DEFAULT_COOKIE_NAME,
                    required = false
            ) String refreshToken,
            @RequestHeader("X-Admin-CSRF") String csrfToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        try {
            AdminSessionBundle session = sessionService.refresh(
                    refreshToken,
                    csrfToken,
                    httpRequest.getHeader("User-Agent")
            );
            writeRefreshCookie(httpResponse, session);
            return ApiResponse.success(toLoginResponse(session));
        } catch (RuntimeException exception) {
            refreshCookieService.clear(httpResponse);
            throw exception;
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(
                    name = AdminRefreshCookieService.DEFAULT_COOKIE_NAME,
                    required = false
            ) String refreshToken,
            @RequestHeader("X-Admin-CSRF") String csrfToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        sessionService.logout(
                refreshToken,
                csrfToken,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        refreshCookieService.clear(httpResponse);
        return ApiResponse.success(
                MessageCodes.AUTH_LOGOUT_SUCCESS,
                "Administrator signed out",
                null
        );
    }

    @Operation(
            summary = "Set password from an internal administrator invitation",
            description = "Consumes a one-time invitation token and activates the account."
    )
    @PostMapping("/setup-password")
    public ApiResponse<Void> setupPassword(
            @Valid @RequestBody SetupAdminPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        invitationService.accept(
                request.token(),
                request.password(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ApiResponse.success(
                MessageCodes.INTERNAL_ADMIN_PASSWORD_SET,
                "Administrator password set; sign in to continue",
                null
        );
    }

    @PostMapping("/password/forgot")
    public ApiResponse<Void> forgotPassword(
            @Valid @RequestBody ForgotAdminPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        passwordResetService.request(
                request.email(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ApiResponse.success(
                MessageCodes.ADMIN_PASSWORD_RESET_REQUEST_ACCEPTED,
                "If the account is eligible, password reset instructions will be sent",
                null
        );
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(
            @Valid @RequestBody ResetAdminPasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        passwordResetService.reset(
                request.token(),
                request.password(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        refreshCookieService.clear(httpResponse);
        return ApiResponse.success(
                MessageCodes.ADMIN_PASSWORD_RESET_COMPLETED,
                "Administrator password changed; sign in again",
                null
        );
    }

    @PostMapping("/password/change")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangeAdminPasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        passwordResetService.change(
                UUID.fromString(jwt.getSubject()),
                request.currentPassword(),
                request.newPassword(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        refreshCookieService.clear(httpResponse);
        return ApiResponse.success(
                MessageCodes.ADMIN_PASSWORD_CHANGED,
                "Administrator password changed; sign in again",
                null
        );
    }

    @Operation(summary = "Get current admin profile", description = "Returns the profile and role of the currently logged-in admin.")
    // [CODE NOTE - UC-03]: API lấy thông tin Admin hiện tại, trả về Permission/Role để Frontend làm menu routing.
    @GetMapping("/me")
    public ApiResponse<AdminProfileResponse> getMe(@AuthenticationPrincipal Jwt jwt) {
        // Jwt is auto-injected by Spring Security BearerTokenAuthenticationFilter
        UUID adminId = UUID.fromString(jwt.getSubject());
        AdminProfileResponse response = adminAuthService.getMe(adminId);
        return ApiResponse.success(response);
    }

    private void writeRefreshCookie(
            HttpServletResponse response,
            AdminSessionBundle session
    ) {
        refreshCookieService.write(
                response,
                session.refreshToken(),
                session.remembered(),
                session.refreshExpiresAt()
        );
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Pragma", "no-cache");
    }

    private LoginResponse toLoginResponse(AdminSessionBundle session) {
        return new LoginResponse(
                session.accessToken(),
                session.csrfToken(),
                session.remembered()
        );
    }
}
