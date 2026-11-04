package com.manabihub.payout.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "manabihub.payout")
@Getter
@Setter
public class WithdrawalOtpProperties {

    /** email keeps the released flow; firebase requires a fresh SMS proof. */
    private String withdrawalOtpMode = "email";

    public boolean isFirebaseMode() {
        return "firebase".equalsIgnoreCase(withdrawalOtpMode == null ? "" : withdrawalOtpMode.trim());
    }
}
