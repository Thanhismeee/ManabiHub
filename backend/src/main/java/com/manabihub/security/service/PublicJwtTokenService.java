package com.manabihub.security.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PublicJwtTokenService {

    private final AppUserRepository appUserRepository;
    private final JwtEncoder jwtEncoder;

    public PublicJwtTokenService(AppUserRepository appUserRepository, JwtEncoder jwtEncoder) {
        this.appUserRepository = appUserRepository;
        this.jwtEncoder = jwtEncoder;
    }

    public String issueCurrentRoleToken(UUID userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.AUTH_UNAUTHORIZED,
                        "User account was not found",
                        HttpStatus.UNAUTHORIZED
                ));
        Instant now = Instant.now();
        String roles = user.getRoles().stream()
                .map(role -> role.getCode().name())
                .sorted()
                .collect(Collectors.joining(" "));

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("self")
                .issuedAt(now)
                .expiresAt(now.plus(24, ChronoUnit.HOURS))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", roles)
                .claim("type", "PUBLIC_USER");
                
        // Copy sid if present in current token
        if (SecurityContextHolder.getContext().getAuthentication() != null &&
            SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof Jwt jwt) {
            if (jwt.hasClaim("sid")) {
                claimsBuilder.claim("sid", jwt.getClaimAsString("sid"));
            }
        }

        JwtClaimsSet claims = claimsBuilder.build();

        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims
        )).getTokenValue();
    }
}
