package com.manabihub.kyc.service.impl;

import com.manabihub.kyc.port.VnptServerVerificationResult;
import com.manabihub.kyc.port.VnptVerificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class FailClosedVnptVerificationAdapter implements VnptVerificationPort {

    private static final Logger log = LoggerFactory.getLogger(FailClosedVnptVerificationAdapter.class);

    @Override
    public VnptServerVerificationResult verifyTransaction(String providerTransactionId, String providerSessionId) {
        log.warn("Using FailClosedVnptVerificationAdapter. VNPT server verification is NOT CONFIGURED.");
        return VnptServerVerificationResult.failure(
                providerTransactionId,
                providerSessionId,
                "NOT_CONFIGURED",
                "PROVIDER_NOT_CONFIGURED"
        );
    }
}
