package com.manabihub.systemconfig.service;

import com.manabihub.systemconfig.entity.SystemSetting;
import com.manabihub.systemconfig.model.CommercialPolicy;
import com.manabihub.systemconfig.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommercialPolicyServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    @InjectMocks
    private CommercialPolicyService service;

    @Test
    void missingSettingsUseTheApprovedSafeDefaults() {
        when(systemSettingRepository.findAllBySettingKeyInOrderBySettingKeyAsc(anyCollection()))
                .thenReturn(List.of());

        CommercialPolicy policy = service.getCurrentPolicy();

        assertEquals("VND", policy.currency());
        assertEquals(new BigDecimal("0.20"), policy.commissionRate());
        assertEquals(14, policy.refundWindowDays());
        assertEquals(20, policy.refundProgressLimitPercent());
        assertEquals(14, policy.escrowHoldingDays());
        assertEquals(new BigDecimal("100000"), policy.payoutThreshold());
        assertEquals(BigDecimal.ZERO, policy.withdrawalFee());
        assertEquals(1, policy.kycTargetDaysMin());
        assertEquals(2, policy.kycTargetDaysMax());
        assertEquals("br-ref-01-2026-08-03", policy.policyVersion());
        assertEquals(Instant.parse("2026-08-03T00:00:00Z"), policy.effectiveAt());
    }

    @Test
    void malformedPresentSettingFailsClosed() {
        when(systemSettingRepository.findAllBySettingKeyInOrderBySettingKeyAsc(anyCollection()))
                .thenReturn(List.of(setting(CommercialPolicyService.COMMISSION_RATE, "twenty")));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.getCurrentPolicy());

        assertEquals(
                "Invalid commercial policy setting COMMISSION_RATE: must be a decimal number",
                exception.getMessage());
    }

    @Test
    void invalidKycRangeFailsClosed() {
        when(systemSettingRepository.findAllBySettingKeyInOrderBySettingKeyAsc(anyCollection()))
                .thenReturn(List.of(
                        setting(CommercialPolicyService.KYC_TARGET_DAYS_MIN, "3"),
                        setting(CommercialPolicyService.KYC_TARGET_DAYS_MAX, "2")));

        assertThrows(IllegalStateException.class, () -> service.getCurrentPolicy());
    }

    @Test
    void candidateUpdateIsCheckedAgainstTheWholePolicy() {
        assertThrows(
                com.manabihub.common.exception.BusinessException.class,
                () -> service.validateCandidate(
                        List.of(
                                setting(CommercialPolicyService.KYC_TARGET_DAYS_MIN, "1"),
                                setting(CommercialPolicyService.KYC_TARGET_DAYS_MAX, "2")),
                        CommercialPolicyService.KYC_TARGET_DAYS_MIN,
                        "3"));
    }

    @Test
    void commissionPrecisionFailsClosedEvenWhenDatabaseWasEditedDirectly() {
        when(systemSettingRepository.findAllBySettingKeyInOrderBySettingKeyAsc(anyCollection()))
                .thenReturn(List.of(
                        setting(CommercialPolicyService.COMMISSION_RATE, "0.12345")));

        assertThrows(IllegalStateException.class, () -> service.getCurrentPolicy());
    }

    private SystemSetting setting(String key, String value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}
