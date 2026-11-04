package com.manabihub.systemconfig.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SystemSettingValidatorTest {

    private final SystemSettingValidator validator = new SystemSettingValidator();

    @Test
    void normalizesSupportedNumericAndBooleanValues() {
        assertEquals("0.2", validator.normalize("COMMISSION_RATE", "0.2000"));
        assertEquals("100000", validator.normalize("PAYOUT_THRESHOLD", "100000.0"));
        assertEquals("true", validator.normalize("AI_ENABLED", "TRUE"));
    }

    @Test
    void rejectsOutOfRangeOrUnsupportedValues() {
        BusinessException commissionError = assertThrows(
                BusinessException.class,
                () -> validator.normalize("COMMISSION_RATE", "1.1")
        );
        assertEquals(MessageCodes.SYSTEM_SETTING_INVALID, commissionError.getMessageCode());

        assertThrows(
                BusinessException.class,
                () -> validator.normalize("ADMIN_LOCKOUT_MAX_ATTEMPTS", "2")
        );
        assertThrows(
                BusinessException.class,
                () -> validator.normalize("COMMISSION_RATE", "0.12345")
        );
        assertThrows(
                BusinessException.class,
                () -> validator.normalize("ESCROW_HOLDING_DAYS", "0")
        );
        assertThrows(
                BusinessException.class,
                () -> validator.normalize("UNKNOWN_SECRET", "value")
        );
    }
}
