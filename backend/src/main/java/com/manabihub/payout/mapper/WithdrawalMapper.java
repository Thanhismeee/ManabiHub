package com.manabihub.payout.mapper;

import com.manabihub.payout.dto.response.WithdrawalRequestResponse;
import com.manabihub.payout.entity.BankAccountSnapshot;
import com.manabihub.payout.entity.WithdrawalRequest;
import com.manabihub.payout.security.PayoutSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WithdrawalMapper {

    private final PayoutSecurityService securityService;

    public WithdrawalRequestResponse toResponse(WithdrawalRequest request) {
        BankAccountSnapshot bank = request.getBankAccountSnapshot();
        return WithdrawalRequestResponse.builder()
                .id(request.getId() == null ? null : request.getId().toString())
                .requestedAmount(request.getRequestedAmount())
                .currency("VND")
                .status(request.getStatus())
                .bankCode(bank == null ? null : bank.getBankCode())
                .bankName(bank == null ? null : bank.getBankName())
                .accountHolderName(bank == null ? null : bank.getAccountHolderName())
                .accountNumberMasked(
                        bank == null
                                ? null
                                : securityService.maskAccountNumber(bank.getAccountNumber())
                )
                .branch(bank == null ? null : bank.getBranch())
                .requestedAt(request.getRequestedAt())
                .reviewedAt(request.getDecidedAt())
                .rejectionReason(request.getDecisionNote())
                .build();
    }
}
