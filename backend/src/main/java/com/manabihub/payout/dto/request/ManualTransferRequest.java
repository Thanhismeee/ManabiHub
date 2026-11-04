package com.manabihub.payout.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class ManualTransferRequest {

    @NotBlank(message = "Transaction reference is required")
    @Size(max = 100, message = "Transaction reference must not exceed 100 characters")
    private String transactionReference;

    @NotNull(message = "Transferred amount is required")
    @Positive(message = "Transferred amount must be greater than zero")
    private BigDecimal transferredAmount;

    @NotNull(message = "Transferred timestamp is required")
    @PastOrPresent(message = "Transferred timestamp cannot be in the future")
    private Instant transferredAt;

    @Size(max = 500, message = "Note must not exceed 500 characters")
    private String note;
}
