package com.manabihub.payout.config;

import com.manabihub.identity.config.FirebasePhoneAuthProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WithdrawalOtpProductionValidatorTest {

    @Test
    void acceptsEmailModeWithoutFirebaseCredentials() {
        WithdrawalOtpProperties otp = new WithdrawalOtpProperties();
        otp.setWithdrawalOtpMode("email");

        assertDoesNotThrow(() ->
                new WithdrawalOtpProductionValidator(
                        otp, new FirebasePhoneAuthProperties()).afterPropertiesSet());
    }

    @Test
    void acceptsFirebaseModeWithMatchingServiceAccount() {
        WithdrawalOtpProperties otp = new WithdrawalOtpProperties();
        otp.setWithdrawalOtpMode("firebase");

        assertDoesNotThrow(() ->
                new WithdrawalOtpProductionValidator(otp, firebaseProperties())
                        .afterPropertiesSet());
    }

    @Test
    void rejectsFirebaseModeWhenFirebaseIsDisabled() {
        WithdrawalOtpProperties otp = new WithdrawalOtpProperties();
        otp.setWithdrawalOtpMode("firebase");

        assertThrows(IllegalStateException.class, () ->
                new WithdrawalOtpProductionValidator(
                        otp, new FirebasePhoneAuthProperties()).afterPropertiesSet());
    }

    private FirebasePhoneAuthProperties firebaseProperties() {
        String json = "{"
                + "\"type\":\"service_account\","
                + "\"project_id\":\"manabihub-demo\","
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
