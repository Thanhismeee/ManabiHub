package com.manabihub.identity.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.manabihub.common.util.PhoneNumberNormalizer;
import com.manabihub.identity.config.FirebasePhoneAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FirebaseAdminPhoneIdentityVerifier implements FirebasePhoneIdentityVerifier {

    private static final String APP_NAME = "manabihub-phone-auth";

    private final FirebasePhoneAuthProperties properties;
    private volatile FirebaseAuth firebaseAuth;

    @Override
    public VerifiedFirebasePhone verify(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw FirebasePhoneVerificationException.invalid("Firebase ID token is missing", null);
        }
        try {
            FirebaseToken decoded = auth().verifyIdToken(idToken, true);
            Map<String, Object> claims = decoded.getClaims();
            String provider = signInProvider(claims.get("firebase"));
            String phoneNumber = stringClaim(claims.get("phone_number"));
            Instant authenticatedAt = instantClaim(claims.get("auth_time"));
            String normalized = PhoneNumberNormalizer.normalize(phoneNumber);
            if (!"phone".equals(provider)
                    || normalized == null
                    || !normalized.matches("0\\d{9}")
                    || authenticatedAt == null) {
                throw FirebasePhoneVerificationException.invalid(
                        "Token is not a Firebase phone-auth proof", null);
            }
            return new VerifiedFirebasePhone(normalized, authenticatedAt, decoded.getUid());
        } catch (FirebasePhoneVerificationException exception) {
            throw exception;
        } catch (FirebaseAuthException | IllegalArgumentException exception) {
            throw FirebasePhoneVerificationException.invalid("Firebase ID token is invalid", exception);
        }
    }

    private FirebaseAuth auth() {
        FirebaseAuth current = firebaseAuth;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (firebaseAuth == null) {
                firebaseAuth = initializeAuth();
            }
            return firebaseAuth;
        }
    }

    private FirebaseAuth initializeAuth() {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getProjectId())) {
            throw FirebasePhoneVerificationException.unavailable(
                    "Firebase Phone Auth is not configured", null);
        }
        try {
            GoogleCredentials credentials;
            if (StringUtils.hasText(properties.getServiceAccountJsonBase64())) {
                byte[] json = Base64.getDecoder().decode(properties.getServiceAccountJsonBase64().trim());
                credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(json));
            } else {
                credentials = GoogleCredentials.getApplicationDefault();
            }
            if (credentials instanceof ServiceAccountCredentials serviceAccount
                    && StringUtils.hasText(serviceAccount.getProjectId())
                    && !properties.getProjectId().trim().equals(serviceAccount.getProjectId().trim())) {
                throw new IllegalArgumentException(
                        "Firebase project ID does not match the service-account project");
            }
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(properties.getProjectId().trim())
                    .build();
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(candidate -> APP_NAME.equals(candidate.getName()))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(options, APP_NAME));
            return FirebaseAuth.getInstance(app);
        } catch (IOException | IllegalArgumentException exception) {
            throw FirebasePhoneVerificationException.unavailable(
                    "Firebase service-account configuration is invalid", exception);
        }
    }

    private String signInProvider(Object firebaseClaim) {
        if (firebaseClaim instanceof Map<?, ?> values) {
            return stringClaim(values.get("sign_in_provider"));
        }
        return null;
    }

    private String stringClaim(Object value) {
        return value instanceof String text ? text.trim() : null;
    }

    private Instant instantClaim(Object value) {
        if (value instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        if (value instanceof String text) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
