package com.manabihub.identity.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.io.IOException;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class PhoneVerificationSmsProductionValidator implements InitializingBean {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PhoneVerificationSmsProperties properties;
    private final FirebasePhoneAuthProperties firebaseProperties;

    @Override
    public void afterPropertiesSet() {
        validate(properties, firebaseProperties);
    }

    static void validate(PhoneVerificationSmsProperties properties) {
        validate(properties, new FirebasePhoneAuthProperties());
    }

    static void validate(
            PhoneVerificationSmsProperties properties,
            FirebasePhoneAuthProperties firebaseProperties
    ) {
        if (properties.getSmsTimeoutSeconds() < 1 || properties.getSmsTimeoutSeconds() > 30) {
            throw invalid("PHONE_VERIFICATION_SMS_TIMEOUT_SECONDS must be between 1 and 30");
        }

        String mode = properties.getSmsMode() == null
                ? ""
                : properties.getSmsMode().trim().toLowerCase();
        if ("firebase".equals(mode)) {
            validateFirebase(firebaseProperties);
            return;
        }
        if ("esms".equals(mode)) {
            PhoneVerificationSmsProperties.Esms esms = properties.getEsms();
            if (esms == null
                    || !StringUtils.hasText(esms.getApiKey())
                    || !StringUtils.hasText(esms.getSecretKey())
                    || !StringUtils.hasText(esms.getBrandname())) {
                throw invalid("eSMS API key, SecretKey, and Brandname are required in production");
            }
            return;
        }

        if ("webhook".equals(mode)) {
            if (!StringUtils.hasText(properties.getSmsWebhookUrl())
                    || !StringUtils.hasText(properties.getSmsApiKey())) {
                throw invalid("SMS webhook URL and API key are required in production");
            }
            URI webhookUri;
            try {
                webhookUri = URI.create(properties.getSmsWebhookUrl());
            } catch (IllegalArgumentException exception) {
                throw invalid("SMS webhook URL must be an absolute HTTPS URL");
            }
            if (!"https".equalsIgnoreCase(webhookUri.getScheme()) || webhookUri.getHost() == null) {
                throw invalid("SMS webhook URL must be an absolute HTTPS URL");
            }
            return;
        }

        throw invalid("PHONE_VERIFICATION_SMS_MODE must be firebase, esms, or webhook in production");
    }

    public static void validateFirebase(FirebasePhoneAuthProperties firebaseProperties) {
        if (firebaseProperties == null
                || !firebaseProperties.isEnabled()
                || !StringUtils.hasText(firebaseProperties.getProjectId())
                || !StringUtils.hasText(firebaseProperties.getServiceAccountJsonBase64())) {
            throw invalid(
                    "Firebase mode requires FIREBASE_PHONE_AUTH_ENABLED, FIREBASE_PROJECT_ID, "
                            + "and FIREBASE_SERVICE_ACCOUNT_JSON_BASE64"
            );
        }
        try {
            String json = new String(
                    Base64.getDecoder().decode(firebaseProperties.getServiceAccountJsonBase64().trim()),
                    StandardCharsets.UTF_8
            );
            JsonNode credentials = OBJECT_MAPPER.readTree(json);
            String credentialProjectId = credentials.path("project_id").asText("").trim();
            String privateKey = credentials.path("private_key").asText("").trim();
            String clientEmail = credentials.path("client_email").asText("").trim();
            if (!"service_account".equals(credentials.path("type").asText())
                    || credentialProjectId.isEmpty()
                    || privateKey.isEmpty()
                    || !privateKey.startsWith("-----BEGIN PRIVATE KEY-----")
                    || clientEmail.isEmpty()) {
                throw invalid("Firebase service-account JSON is missing required service-account fields");
            }
            if (!firebaseProperties.getProjectId().trim().equals(credentialProjectId)) {
                throw invalid("FIREBASE_PROJECT_ID does not match the service-account project_id");
            }
        } catch (IllegalArgumentException | IOException exception) {
            throw invalid("FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 is not valid Base64 JSON");
        }
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Phone verification SMS configuration is invalid: " + message);
    }
}
