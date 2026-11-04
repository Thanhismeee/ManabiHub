package com.manabihub.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "manabihub.ai.chat")
@Getter
@Setter
public class AiChatProviderProperties {

    private String baseUrl = "";
    private String endpoint = "/v1/chat/completions";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private int rateLimitPerMinute = 10;
    private int dailyLimit = 50;
    private int timeoutSeconds = 20;
}
