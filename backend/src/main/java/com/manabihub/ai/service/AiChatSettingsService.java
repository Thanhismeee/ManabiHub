package com.manabihub.ai.service;

import com.manabihub.ai.config.AiChatProviderProperties;
import com.manabihub.systemconfig.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AiChatSettingsService {

    private static final BigDecimal FAIL_CLOSED_PRICE_FLOOR = new BigDecimal("999999999999.99");

    private final SystemSettingRepository systemSettingRepository;
    private final AiChatProviderProperties providerProperties;

    public AiChatSettings getSettings() {
        return new AiChatSettings(
                getBoolean("AI_ENABLED", false),
                getBoolean("AI_CHATBOT_ENABLED", false),
                getBoolean("AI_WRITING_ENABLED", false),
                getDecimal("AI_SUPPORT_PRICE_FLOOR", FAIL_CLOSED_PRICE_FLOOR),
                Math.max(1, providerProperties.getRateLimitPerMinute()),
                Math.max(1, providerProperties.getDailyLimit())
        );
    }

    private boolean getBoolean(String settingKey, boolean defaultValue) {
        return systemSettingRepository.findBySettingKey(settingKey)
                .map(setting -> Boolean.parseBoolean(setting.getSettingValue()))
                .orElse(defaultValue);
    }

    private BigDecimal getDecimal(String settingKey, BigDecimal defaultValue) {
        return systemSettingRepository.findBySettingKey(settingKey)
                .map(setting -> parseDecimal(setting.getSettingValue(), defaultValue))
                .orElse(defaultValue);
    }

    private BigDecimal parseDecimal(String value, BigDecimal defaultValue) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    public record AiChatSettings(
            boolean aiEnabled,
            boolean chatbotEnabled,
            boolean aiWritingEnabled,
            BigDecimal priceFloor,
            int rateLimitPerMinute,
            int dailyLimit
    ) {
    }
}
