package com.manabihub.kyc.service;

import java.util.List;
import java.util.Map;

public record VnptSdkDecision(
        boolean verified,
        Map<String, String> identityOcr,
        List<String> failureReasons
) {
}
