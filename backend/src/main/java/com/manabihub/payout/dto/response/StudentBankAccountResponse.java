package com.manabihub.payout.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class StudentBankAccountResponse {
    UUID id;
    String bankCode;
    String bankName;
    String accountNumber;
    String accountHolderName;
    String branch;
    boolean isDefault;
    boolean ownershipVerified;
}
