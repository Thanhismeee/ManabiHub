package com.manabihub.payout.config;

import com.manabihub.identity.config.FirebasePhoneAuthProperties;
import com.manabihub.identity.config.PhoneVerificationSmsProductionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class WithdrawalOtpProductionValidator implements InitializingBean {

    private final WithdrawalOtpProperties otpProperties;
    private final FirebasePhoneAuthProperties firebaseProperties;

    @Override
    public void afterPropertiesSet() {
        String mode = otpProperties.getWithdrawalOtpMode() == null
                ? ""
                : otpProperties.getWithdrawalOtpMode().trim().toLowerCase();
        if ("firebase".equals(mode)) {
            PhoneVerificationSmsProductionValidator.validateFirebase(firebaseProperties);
            return;
        }
        if (!"email".equals(mode)) {
            throw new IllegalStateException(
                    "Withdrawal OTP configuration is invalid: WITHDRAWAL_OTP_MODE must be email or firebase");
        }
    }
}
