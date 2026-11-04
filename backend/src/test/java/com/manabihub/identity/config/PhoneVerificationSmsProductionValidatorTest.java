package com.manabihub.identity.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneVerificationSmsProductionValidatorTest {

    @Test
    void validate_AcceptsCompleteEsmsConfiguration() {
        PhoneVerificationSmsProperties properties = esmsProperties();

        assertDoesNotThrow(() -> PhoneVerificationSmsProductionValidator.validate(properties));
    }

    @Test
    void validate_RejectsMissingEsmsSecret() {
        PhoneVerificationSmsProperties properties = esmsProperties();
        properties.getEsms().setSecretKey(" ");

        assertThrows(
                IllegalStateException.class,
                () -> PhoneVerificationSmsProductionValidator.validate(properties)
        );
    }

    @Test
    void validate_RejectsDisabledProductionMode() {
        PhoneVerificationSmsProperties properties = esmsProperties();
        properties.setSmsMode("disabled");

        assertThrows(
                IllegalStateException.class,
                () -> PhoneVerificationSmsProductionValidator.validate(properties)
        );
    }

    @Test
    void validate_RejectsInsecureWebhookUrl() {
        PhoneVerificationSmsProperties properties = new PhoneVerificationSmsProperties();
        properties.setSmsMode("webhook");
        properties.setSmsWebhookUrl("http://provider.example/messages");
        properties.setSmsApiKey("secret");

        assertThrows(
                IllegalStateException.class,
                () -> PhoneVerificationSmsProductionValidator.validate(properties)
        );
    }

    @Test
    void validate_AcceptsMatchingFirebaseServiceAccount() {
        PhoneVerificationSmsProperties properties = new PhoneVerificationSmsProperties();
        properties.setSmsMode("firebase");
        FirebasePhoneAuthProperties firebase = firebaseProperties("manabihub-demo");

        assertDoesNotThrow(() ->
                PhoneVerificationSmsProductionValidator.validate(properties, firebase));
    }

    @Test
    void validate_RejectsFirebaseCredentialFromAnotherProject() {
        PhoneVerificationSmsProperties properties = new PhoneVerificationSmsProperties();
        properties.setSmsMode("firebase");
        FirebasePhoneAuthProperties firebase = firebaseProperties("another-project");

        assertThrows(
                IllegalStateException.class,
                () -> PhoneVerificationSmsProductionValidator.validate(properties, firebase)
        );
    }

    private PhoneVerificationSmsProperties esmsProperties() {
        PhoneVerificationSmsProperties properties = new PhoneVerificationSmsProperties();
        properties.setSmsMode("esms");
        properties.setSmsTimeoutSeconds(5);
        properties.getEsms().setApiKey("api-key");
        properties.getEsms().setSecretKey("secret-key");
        properties.getEsms().setBrandname("ManabiHub");
        return properties;
    }

    private FirebasePhoneAuthProperties firebaseProperties(String credentialProjectId) {
        String json = "{"
                + "\"type\":\"service_account\","
                + "\"project_id\":\"" + credentialProjectId + "\","
                + "\"private_key\":\"-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----\\n\","
                + "\"client_email\":\"firebase-adminsdk@manabihub-demo.iam.gserviceaccount.com\""
                + "}";
        FirebasePhoneAuthProperties firebase = new FirebasePhoneAuthProperties();
        firebase.setEnabled(true);
        firebase.setProjectId("manabihub-demo");
        firebase.setServiceAccountJsonBase64(Base64.getEncoder().encodeToString(
                json.getBytes(StandardCharsets.UTF_8)));
        return firebase;
    }
}
