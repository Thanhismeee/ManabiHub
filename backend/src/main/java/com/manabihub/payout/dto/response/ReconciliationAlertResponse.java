package com.manabihub.payout.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReconciliationAlertResponse {
    String code;
    String severity;
    String message;
}
