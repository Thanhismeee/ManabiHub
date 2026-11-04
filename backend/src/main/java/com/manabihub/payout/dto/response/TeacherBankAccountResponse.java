package com.manabihub.payout.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherBankAccountResponse {
    private String id;
    private String bankCode;
    private String bankName;
    private String accountNumber;
    private String accountHolderName;
    private String branch;
    private boolean isDefault;
}
