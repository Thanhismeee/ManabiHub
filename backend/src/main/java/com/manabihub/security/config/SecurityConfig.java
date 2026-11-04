package com.manabihub.security.config;

import com.manabihub.security.oauth2.CustomOAuth2UserService;
import com.manabihub.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.manabihub.security.oauth2.OAuth2AuthenticationSuccessHandler;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

import javax.crypto.spec.SecretKeySpec;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.secret:defaultSecretKeyThatIsAtLeast32BytesLongForHS256Algorithm}")
    private String jwtSecret;

    @Value("${CORS_ALLOWED_ORIGINS:*}")
    private List<String> allowedOrigins;

    private final TeacherEligibilityFilter teacherEligibilityFilter;
    private final InternalAdminRoleFilter internalAdminRoleFilter;
    private final AppUserStatusFilter appUserStatusFilter;
    private final PublicUserSessionFilter publicUserSessionFilter;
    private final Environment environment;

    public SecurityConfig(
            TeacherEligibilityFilter teacherEligibilityFilter,
            InternalAdminRoleFilter internalAdminRoleFilter,
            AppUserStatusFilter appUserStatusFilter,
            PublicUserSessionFilter publicUserSessionFilter,
            Environment environment
    ) {
        this.teacherEligibilityFilter = teacherEligibilityFilter;
        this.internalAdminRoleFilter = internalAdminRoleFilter;
        this.appUserStatusFilter = appUserStatusFilter;
        this.publicUserSessionFilter = publicUserSessionFilter;
        this.environment = environment;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(
                        "/api/**",
                        "/actuator/**",
                        "/uploads/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/v1/demo/**",
                                "/api/v1/mock/**",
                                "/api/v1/course-categories",
                                "/api/v1/public/courses/**",
                                "/api/v1/public/teachers/**",
                                "/api/v1/payments/vnpay/ipn",
                                "/uploads/course-thumbnails/**",
                                "/uploads/user-avatars/**",
                                "/api/admin/auth/login",
                                "/api/admin/auth/setup-password",
                                "/api/admin/auth/refresh",
                                "/api/admin/auth/logout",
                                "/api/admin/auth/password/forgot",
                                "/api/admin/auth/password/reset")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/public/commercial-policy/current")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        http.addFilterAfter(teacherEligibilityFilter, BearerTokenAuthenticationFilter.class);
        http.addFilterAfter(internalAdminRoleFilter, BearerTokenAuthenticationFilter.class);
        http.addFilterAfter(appUserStatusFilter, BearerTokenAuthenticationFilter.class);
        http.addFilterAfter(publicUserSessionFilter, AppUserStatusFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain oauth2SecurityFilterChain(
            HttpSecurity http,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler,
            OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler
    ) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService))
                        .successHandler(oauth2AuthenticationSuccessHandler)
                        .failureHandler(oauth2AuthenticationFailureHandler));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch("prod"::equalsIgnoreCase);

        if (allowedOrigins.size() == 1 && "*".equals(allowedOrigins.get(0))) {
            if (production) {
                throw new IllegalStateException(
                        "CORS_ALLOWED_ORIGINS must list exact trusted origins in production"
                );
            }
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            configuration.setAllowedOrigins(allowedOrigins);
        }

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Location", "X-Correlation-ID"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecret.getBytes()));
    }

    private JwtDecoder createNimbusDecoder() {
        SecretKeySpec secretKey = new SecretKeySpec(jwtSecret.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    @ConditionalOnProperty(name = "manabihub.security.mock-jwt", havingValue = "true")
    public JwtDecoder mockJwtDecoder() {
        JwtDecoder realDecoder = createNimbusDecoder();
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);
        return token -> {
            if (token.contains(".")) {
                try {
                    return realDecoder.decode(token);
                } catch (Exception e) {
                    throw new JwtException("Invalid real token: " + e.getMessage(), e);
                }
            }
            
            try {
                // It's a raw base64 string from testing scripts (e.g., verify.js)
                String payloadJson = new String(java.util.Base64.getDecoder().decode(token));
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.Map<String, Object> payloadMap = mapper.readValue(payloadJson, java.util.Map.class);
                
                if (!payloadMap.containsKey("sub") || !payloadMap.containsKey("role")) {
                    throw new JwtException("Mock token payload is missing required claims 'sub' or 'role'");
                }
                
                return org.springframework.security.oauth2.jwt.Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .claim("sub", payloadMap.get("sub").toString())
                        .claim("role", payloadMap.get("role").toString())
                        .build();
            } catch (Exception ex) {
                log.error("Failed to parse mock token payload: {}", token, ex);
                throw new JwtException("Malformed mock token: " + ex.getMessage(), ex);
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "manabihub.security.mock-jwt", havingValue = "false", matchIfMissing = true)
    public JwtDecoder jwtDecoder() {
        return createNimbusDecoder();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("role");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

    @Bean
    public FilterRegistrationBean<TeacherEligibilityFilter> teacherEligibilityFilterRegistration(
            TeacherEligibilityFilter filter
    ) {
        FilterRegistrationBean<TeacherEligibilityFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<InternalAdminRoleFilter> internalAdminRoleFilterRegistration(
            InternalAdminRoleFilter filter
    ) {
        FilterRegistrationBean<InternalAdminRoleFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AppUserStatusFilter> appUserStatusFilterRegistration(
            AppUserStatusFilter filter
    ) {
        FilterRegistrationBean<AppUserStatusFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<PublicUserSessionFilter> publicUserSessionFilterRegistration(
            PublicUserSessionFilter filter
    ) {
        FilterRegistrationBean<PublicUserSessionFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

}
