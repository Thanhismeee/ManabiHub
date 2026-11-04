package com.manabihub.identity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "manabihub.phone-verification")
@Getter
@Setter
public class PhoneVerificationSmsProperties {

    private String smsMode = "console";
    private String smsWebhookUrl = "";
    private String smsApiKey = "";
    private int smsTimeoutSeconds = 5;
    private Esms esms = new Esms();

    @Getter
    @Setter
    public static class Esms {
        private String apiKey = "";
        private String secretKey = "";
        private String brandname = "";
        private boolean sandbox = true;
    }
}
