package com.manabihub.identity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "manabihub.firebase-phone-auth")
@Getter
@Setter
public class FirebasePhoneAuthProperties {

    private boolean enabled = false;
    private String projectId = "";
    /** Base64-encoded service-account JSON. This is a server secret. */
    private String serviceAccountJsonBase64 = "";
}
