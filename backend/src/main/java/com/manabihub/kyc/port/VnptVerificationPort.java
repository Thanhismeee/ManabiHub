package com.manabihub.kyc.port;

/**
 * Port for server-to-server verification of VNPT eKYC transactions.
 * <p>
 * Implementations must verify a provider transaction ID against the VNPT backend,
 * confirming that the identity verification actually occurred and was not fabricated
 * by a malicious client.
 */
public interface VnptVerificationPort {

    /**
     * Verifies a VNPT eKYC transaction server-to-server.
     *
     * @param providerTransactionId the transaction ID returned by VNPT SDK
     * @param providerSessionId     the session ID from the VNPT SDK session
     * @return verification result with server-confirmed data
     */
    VnptServerVerificationResult verifyTransaction(
            String providerTransactionId,
            String providerSessionId
    );
}
