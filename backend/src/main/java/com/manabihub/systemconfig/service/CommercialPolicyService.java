package com.manabihub.systemconfig.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.systemconfig.entity.SystemSetting;
import com.manabihub.systemconfig.model.CommercialPolicy;
import com.manabihub.systemconfig.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommercialPolicyService implements ApplicationRunner {

    public static final String CURRENCY = "CURRENCY";
    public static final String COMMISSION_RATE = "COMMISSION_RATE";
    public static final String REFUND_WINDOW_DAYS = "REFUND_WINDOW_DAYS";
    public static final String REFUND_PROGRESS_LIMIT_PERCENT = "REFUND_PROGRESS_LIMIT_PERCENT";
    public static final String ESCROW_HOLDING_DAYS = "ESCROW_HOLDING_DAYS";
    public static final String PAYOUT_THRESHOLD = "PAYOUT_THRESHOLD";
    public static final String WITHDRAWAL_FEE = "WITHDRAWAL_FEE";
    public static final String KYC_TARGET_DAYS_MIN = "KYC_TARGET_DAYS_MIN";
    public static final String KYC_TARGET_DAYS_MAX = "KYC_TARGET_DAYS_MAX";
    public static final String POLICY_VERSION = "POLICY_VERSION";
    public static final String POLICY_EFFECTIVE_AT = "POLICY_EFFECTIVE_AT";

    private static final List<String> POLICY_KEYS = List.of(
            CURRENCY,
            COMMISSION_RATE,
            REFUND_WINDOW_DAYS,
            REFUND_PROGRESS_LIMIT_PERCENT,
            ESCROW_HOLDING_DAYS,
            PAYOUT_THRESHOLD,
            WITHDRAWAL_FEE,
            KYC_TARGET_DAYS_MIN,
            KYC_TARGET_DAYS_MAX,
            POLICY_VERSION,
            POLICY_EFFECTIVE_AT);

    private static final Map<String, String> SAFE_DEFAULTS = Map.ofEntries(
            Map.entry(CURRENCY, "VND"),
            Map.entry(COMMISSION_RATE, "0.20"),
            Map.entry(REFUND_WINDOW_DAYS, "14"),
            Map.entry(REFUND_PROGRESS_LIMIT_PERCENT, "20"),
            Map.entry(ESCROW_HOLDING_DAYS, "14"),
            Map.entry(PAYOUT_THRESHOLD, "100000"),
            Map.entry(WITHDRAWAL_FEE, "0"),
            Map.entry(KYC_TARGET_DAYS_MIN, "1"),
            Map.entry(KYC_TARGET_DAYS_MAX, "2"),
            Map.entry(POLICY_VERSION, "br-ref-01-2026-08-03"),
            Map.entry(POLICY_EFFECTIVE_AT, "2026-08-03T00:00:00Z"));

    private final SystemSettingRepository systemSettingRepository;

    @Override
    public void run(ApplicationArguments args) {
        getCurrentPolicy();
    }

    public CommercialPolicy getCurrentPolicy() {
        return buildPolicy(toValueMap(systemSettingRepository
                .findAllBySettingKeyInOrderBySettingKeyAsc(POLICY_KEYS)));
    }

    public boolean isPolicyKey(String key) {
        return POLICY_KEYS.contains(key);
    }

    public List<String> policyKeys() {
        return POLICY_KEYS;
    }

    public void validateCandidate(
            List<SystemSetting> lockedSettings,
            String key,
            String normalizedValue
    ) {
        if (!isPolicyKey(key)) {
            return;
        }

        Map<String, String> values = toValueMap(lockedSettings);
        values.put(key, normalizedValue);
        try {
            buildPolicy(values);
        } catch (IllegalStateException exception) {
            throw new BusinessException(
                    MessageCodes.SYSTEM_SETTING_INVALID,
                    exception.getMessage()
            );
        }
    }

    private CommercialPolicy buildPolicy(Map<String, String> values) {
        String currency = text(values, CURRENCY);
        BigDecimal commissionRate = decimal(values, COMMISSION_RATE);
        int refundWindowDays = integer(values, REFUND_WINDOW_DAYS);
        int refundProgressLimitPercent = integer(values, REFUND_PROGRESS_LIMIT_PERCENT);
        int escrowHoldingDays = integer(values, ESCROW_HOLDING_DAYS);
        BigDecimal payoutThreshold = decimal(values, PAYOUT_THRESHOLD);
        BigDecimal withdrawalFee = decimal(values, WITHDRAWAL_FEE);
        int kycTargetDaysMin = integer(values, KYC_TARGET_DAYS_MIN);
        int kycTargetDaysMax = integer(values, KYC_TARGET_DAYS_MAX);
        String policyVersion = text(values, POLICY_VERSION);
        Instant effectiveAt = instant(values, POLICY_EFFECTIVE_AT);

        require(currency.matches("[A-Z]{3,10}"), CURRENCY, "must be an uppercase currency code");
        require(inRange(commissionRate, BigDecimal.ZERO, BigDecimal.ONE),
                COMMISSION_RATE, "must be between 0 and 1");
        require(commissionRate.stripTrailingZeros().scale() <= 4,
                COMMISSION_RATE, "must have at most 4 decimal places");
        require(refundWindowDays >= 0 && refundWindowDays <= 365,
                REFUND_WINDOW_DAYS, "must be between 0 and 365");
        require(refundProgressLimitPercent >= 0 && refundProgressLimitPercent <= 100,
                REFUND_PROGRESS_LIMIT_PERCENT, "must be between 0 and 100");
        require(escrowHoldingDays >= 1 && escrowHoldingDays <= 365,
                ESCROW_HOLDING_DAYS, "must be between 1 and 365");
        require(payoutThreshold.signum() >= 0, PAYOUT_THRESHOLD, "must not be negative");
        require(withdrawalFee.signum() >= 0, WITHDRAWAL_FEE, "must not be negative");
        require(kycTargetDaysMin >= 1, KYC_TARGET_DAYS_MIN, "must be at least 1");
        require(kycTargetDaysMax >= kycTargetDaysMin,
                KYC_TARGET_DAYS_MAX, "must not be lower than the minimum");

        return new CommercialPolicy(
                currency,
                commissionRate,
                refundWindowDays,
                refundProgressLimitPercent,
                escrowHoldingDays,
                payoutThreshold,
                withdrawalFee,
                kycTargetDaysMin,
                kycTargetDaysMax,
                policyVersion,
                effectiveAt);
    }

    private Map<String, String> toValueMap(List<SystemSetting> settings) {
        return settings.stream()
                .collect(Collectors.toMap(
                        SystemSetting::getSettingKey,
                        SystemSetting::getSettingValue,
                        (first, ignored) -> first));
    }

    private String text(Map<String, String> values, String key) {
        String value = values.getOrDefault(key, SAFE_DEFAULTS.get(key));
        if (!StringUtils.hasText(value)) {
            throw invalid(key, "must not be blank");
        }
        return value.trim();
    }

    private BigDecimal decimal(Map<String, String> values, String key) {
        try {
            return new BigDecimal(text(values, key));
        } catch (NumberFormatException exception) {
            throw invalid(key, "must be a decimal number", exception);
        }
    }

    private int integer(Map<String, String> values, String key) {
        try {
            return Integer.parseInt(text(values, key));
        } catch (NumberFormatException exception) {
            throw invalid(key, "must be an integer", exception);
        }
    }

    private Instant instant(Map<String, String> values, String key) {
        try {
            return Instant.parse(text(values, key));
        } catch (DateTimeParseException exception) {
            throw invalid(key, "must be an ISO-8601 instant", exception);
        }
    }

    private boolean inRange(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        return value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }

    private void require(boolean condition, String key, String requirement) {
        if (!condition) {
            throw invalid(key, requirement);
        }
    }

    private IllegalStateException invalid(String key, String requirement) {
        return new IllegalStateException("Invalid commercial policy setting " + key + ": " + requirement);
    }

    private IllegalStateException invalid(String key, String requirement, Exception cause) {
        return new IllegalStateException(
                "Invalid commercial policy setting " + key + ": " + requirement,
                cause);
    }
}
