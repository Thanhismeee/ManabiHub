package com.manabihub.kyc.service.impl;

import com.manabihub.kyc.port.VnptVerificationPort;
import com.manabihub.kyc.port.VnptServerVerificationResult;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Primary
@Profile("test")
public class TestVnptVerificationAdapter implements VnptVerificationPort {

    private static final Map<String, TestBinding> allowedBindings = new ConcurrentHashMap<>();

    private record TestBinding(String sessionId, String serverIdNumber, String fullName, String dateOfBirth) {}

    public static void allowTransaction(String txId, String sessionId, String serverIdNumber, String fullName, String dateOfBirth) {
        allowedBindings.put(txId, new TestBinding(sessionId, serverIdNumber, fullName, dateOfBirth));
    }

    public static void clear() {
        allowedBindings.clear();
    }

    @Override
    public VnptServerVerificationResult verifyTransaction(String providerTransactionId, String providerSessionId) {
        if (providerTransactionId == null || providerSessionId == null) {
            return VnptServerVerificationResult.failure(
                    providerTransactionId,
                    providerSessionId,
                    "BAD_REQUEST",
                    "MISSING_PARAMS"
            );
        }

        TestBinding binding = allowedBindings.get(providerTransactionId);
        if (binding != null && providerSessionId.equals(binding.sessionId())) {
            return VnptServerVerificationResult.success(
                    providerTransactionId,
                    providerSessionId,
                    "SUCCESS",
                    Instant.now(),
                    binding.serverIdNumber(),
                    binding.fullName(),
                    binding.dateOfBirth(),
                    "ref-" + providerTransactionId
            );
        }
        return VnptServerVerificationResult.failure(
                providerTransactionId,
                providerSessionId,
                "NOT_FOUND",
                "TX_NOT_FOUND"
        );
    }
}
