package com.manabihub.payout.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.payout.service.StudentBankOwnershipVerificationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile("!local & !test")
public class UnavailableStudentBankOwnershipVerificationService
        implements StudentBankOwnershipVerificationService {

    @Override
    public VerificationEvidence verify(boolean ownershipConfirmed) {
        throw new BusinessException(
                MessageCodes.PAYOUT_BANK_VERIFICATION_UNAVAILABLE,
                "Student identity and bank-account ownership verification is not configured",
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
