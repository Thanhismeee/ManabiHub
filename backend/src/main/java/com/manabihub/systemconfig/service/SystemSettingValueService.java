package com.manabihub.systemconfig.service;

import com.manabihub.systemconfig.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Typed, read-only access to runtime settings for downstream business services.
 * A missing or malformed database value falls back to the caller's safe default.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemSettingValueService {

    private final SystemSettingRepository systemSettingRepository;

    public BigDecimal getDecimal(String key, BigDecimal fallback) {
        return systemSettingRepository.findBySettingKey(key)
                .map(setting -> parseDecimal(setting.getSettingValue(), fallback))
                .orElse(fallback);
    }

    public int getInteger(String key, int fallback) {
        return systemSettingRepository.findBySettingKey(key)
                .map(setting -> parseInteger(setting.getSettingValue(), fallback))
                .orElse(fallback);
    }

    public boolean getBoolean(String key, boolean fallback) {
        return systemSettingRepository.findBySettingKey(key)
                .map(setting -> parseBoolean(setting.getSettingValue(), fallback))
                .orElse(fallback);
    }

    private BigDecimal parseDecimal(String value, BigDecimal fallback) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private int parseInteger(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean parseBoolean(String value, boolean fallback) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }
}
