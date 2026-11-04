package com.manabihub.payout.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.payout.service.StudentBankOwnershipVerificationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Profile({"local", "test"})
public class MockStudentBankOwnershipVerificationService
        implements StudentBankOwnershipVerificationService {

    @Override
    public VerificationEvidence verify(boolean ownershipConfirmed) {
        if (!ownershipConfirmed) {
            throw new BusinessException(
                    MessageCodes.PAYOUT_BANK_OWNERSHIP_REQUIRED,
                    "Confirm the simulated identity and bank-account ownership check",
                    HttpStatus.BAD_REQUEST);
        }
        return new VerificationEvidence(true, "MOCK_LOCAL", LocalDateTime.now());
    }
}
