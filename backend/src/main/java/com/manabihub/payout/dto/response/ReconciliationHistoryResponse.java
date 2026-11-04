package com.manabihub.payout.dto.response;

import com.manabihub.payout.enums.ReconciliationStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class ReconciliationHistoryResponse {
    UUID id;
    String triggerType;
    ReconciliationStatus status;
    List<ReconciliationAlertResponse> alerts;
    UUID checkedBy;
    Instant createdAt;
}
