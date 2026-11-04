package com.manabihub.learning.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CertificateEligibilityResponse(
        boolean eligible,
        boolean progressComplete,
        boolean requiredAssignmentsComplete,
        boolean exerciseScoreSatisfied,
        BigDecimal exerciseAverageScore,
        int exerciseScoreThreshold,
        boolean finalTestPassed,
        List<String> reasons
) {
}
