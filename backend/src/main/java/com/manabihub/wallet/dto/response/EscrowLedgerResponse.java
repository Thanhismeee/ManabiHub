package com.manabihub.wallet.dto.response;

import com.manabihub.wallet.enums.EscrowStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class EscrowLedgerResponse {
    UUID id;
    UUID orderId;
    String courseName;
    BigDecimal grossAmount;
    BigDecimal platformCommissionAmount;
    BigDecimal teacherNetAmount;
    EscrowStatus status;
    Instant releaseAt;
    Instant createdAt;
}
