package com.manabihub.security.oauth2;

import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.repository.StudentProfileRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import com.manabihub.identity.entity.PublicUserSession;
import com.manabihub.identity.service.PublicUserSessionService;
import com.manabihub.common.exception.BusinessException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

        private final JwtEncoder jwtEncoder;
        private final StudentProfileRepository studentProfileRepository;
        private final PublicUserSessionService publicUserSessionService;

        @Value("${app.frontend.onboarding-url:http://localhost:5173/onboarding/student}")
        private String frontendOnboardingUrl;

        @Value("${app.frontend.success-url:http://localhost:5173/auth/callback}")
        private String frontendSuccessUrl;

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                        Authentication authentication) throws IOException, ServletException {

                CustomOAuth2User oauth2User = (CustomOAuth2User) authentication.getPrincipal();
                AppUser appUser = oauth2User.getAppUser();

                String deviceId = getOrCreateDeviceIdCookie(request, response);
                
                PublicUserSession session;
                try {
                        String userAgent = request.getHeader("User-Agent");
                        session = publicUserSessionService.createSession(appUser.getId(), deviceId, userAgent, getClientDeviceName(userAgent));
                } catch (BusinessException ex) {
                        log.warn("OAuth2 login blocked for user {} due to device limit", appUser.getId());
                        getRedirectStrategy().sendRedirect(request, response, getLoginRoute() + "?error=PUBLIC_DEVICE_LIMIT_REACHED");
                        return;
                }

                // 1. Generate JWT
                String token = generateJwtToken(appUser, session.getId());

                // 2. Check onboarding status based on StudentProfile existence
                boolean isOnboardingCompleted = studentProfileRepository.existsByUserId(appUser.getId());

                // 3. Redirect to appropriate URL
                String targetUrl = isOnboardingCompleted ? frontendSuccessUrl + "?token=" + token
                                : frontendOnboardingUrl + "?token=" + token;

                log.info("OAuth2 login successful for user {}", appUser.getId());
                getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }

        private String getLoginRoute() {
                try {
                        java.net.URL url = new java.net.URL(frontendSuccessUrl);
                        return url.getProtocol() + "://" + url.getHost() + (url.getPort() != -1 ? ":" + url.getPort() : "") + "/login";
                } catch (Exception e) {
                        return "http://localhost:5173/login";
                }
        }

        private String getOrCreateDeviceIdCookie(HttpServletRequest request, HttpServletResponse response) {
                if (request.getCookies() != null) {
                        for (Cookie cookie : request.getCookies()) {
                                if ("MHB_DEVICE_ID".equals(cookie.getName())) {
                                        return cookie.getValue();
                                }
                        }
                }
                String newDeviceId = UUID.randomUUID().toString();
                Cookie cookie = new Cookie("MHB_DEVICE_ID", newDeviceId);
                cookie.setHttpOnly(true);
                cookie.setSecure(request.isSecure());
                cookie.setPath("/");
                cookie.setMaxAge(365 * 24 * 60 * 60); // 1 year
                cookie.setAttribute("SameSite", "Lax");
                response.addCookie(cookie);
                return newDeviceId;
        }

        private String getClientDeviceName(String userAgent) {
                if (userAgent == null) return "Unknown Device";
                if (userAgent.contains("Windows")) return "Windows Device";
                if (userAgent.contains("Mac")) return "Mac Device";
                if (userAgent.contains("iPhone")) return "iPhone";
                if (userAgent.contains("iPad")) return "iPad";
                if (userAgent.contains("Android")) return "Android Device";
                return "Unknown Device";
        }

        private String generateJwtToken(AppUser appUser, UUID sessionId) {
                Instant now = Instant.now();

                // Match the algorithm used in SecurityConfig (HS256)
                JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();

                String roles = appUser.getRoles().stream()
                                .map(role -> role.getCode().name())
                                .collect(Collectors.joining(" "));

                JwtClaimsSet claims = JwtClaimsSet.builder()
                                .issuer("self")
                                .issuedAt(now)
                                .expiresAt(now.plus(24, ChronoUnit.HOURS))
                                .subject(appUser.getId().toString())
                                .claim("email", appUser.getEmail())
                                .claim("role", roles)
                                .claim("type", "PUBLIC_USER")
                                .claim("sid", sessionId.toString())
                                .build();

                return this.jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
        }
}
