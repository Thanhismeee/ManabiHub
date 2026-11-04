package com.manabihub.common.demo;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * Demo-only controller for verifying the API response convention.
 * <p>
 * <b>DO NOT use in production.</b> This controller exists solely to validate
 * the response envelope, exception handling, and message code patterns
 * during Iteration 0 setup.
 * <p>
 * TODO: Remove or disable this controller before production release.
 */
@RestController
@RequestMapping("/api/v1/demo")
@Profile("local")
public class DemoController {

    /**
     * Demonstrates a successful response.
     */
    @GetMapping("/success")
    public ResponseEntity<ApiResponse<Map<String, String>>> demoSuccess() {
        Map<String, String> data = Map.of(
                "greeting", "Hello from ManabiHub!",
                "status", "API response convention is working"
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * Demo login for Admin KYC local testing.
     */
    @PostMapping("/login-admin")
    public ResponseEntity<ApiResponse<Map<String, String>>> demoLoginAdmin(HttpServletRequest request) {
        Jwt jwt = Jwt.withTokenValue("mock-session-token")
                .header("alg", "none")
                .claim("sub", "c0000000-0000-0000-0000-000000000002")
                .claim("role", "COURSE_MANAGER")
                .claim("email", "manager@manabihub.local")
                .build();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_COURSE_MANAGER")));

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(auth);
        SecurityContextHolder.setContext(securityContext);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        Map<String, String> data = Map.of(
                "status", "Mock session created",
                "adminId", "c0000000-0000-0000-0000-000000000002"
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * Demonstrates a business exception response.
     */
    @GetMapping("/business-error")
    public ResponseEntity<ApiResponse<Void>> demoBusinessError() {
        throw new BusinessException(
                MessageCodes.COURSE_NOT_FOUND,
                "Course with the given ID was not found"
        );
    }

    /**
     * Demonstrates a business exception with a non-400 status.
     */
    @GetMapping("/not-found-error")
    public ResponseEntity<ApiResponse<Void>> demoNotFoundError() {
        throw new BusinessException(
                MessageCodes.COURSE_NOT_FOUND,
                "Course with the given ID was not found",
                HttpStatus.NOT_FOUND
        );
    }

    /**
     * Demonstrates a validation error response.
     */
    @PostMapping("/validation-error")
    public ResponseEntity<ApiResponse<Void>> demoValidationError(
            @Valid @RequestBody DemoRequest request) {
        // If validation passes, return success
        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Validation passed"
        ));
    }

    @Data
    static class DemoRequest {
        @NotBlank(message = "Name must not be blank")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        private String name;

        @NotBlank(message = "Email must not be blank")
        private String email;
    }
}
