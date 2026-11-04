package com.manabihub.payout.service;

import com.manabihub.payout.dto.request.CreateWithdrawalRequest;
import com.manabihub.payout.dto.response.StudentBankAccountResponse;
import com.manabihub.payout.dto.response.WithdrawalRequestResponse;
import com.manabihub.payout.dto.response.WithdrawalOtpResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface StudentWithdrawalService {
    WithdrawalRequestResponse createWithdrawal(UUID userId, CreateWithdrawalRequest request);
    Page<WithdrawalRequestResponse> getMyWithdrawals(UUID userId, Pageable pageable);
    WithdrawalRequestResponse getMyWithdrawal(UUID userId, UUID withdrawalId);
    WithdrawalRequestResponse cancelWithdrawal(UUID userId, UUID withdrawalId);
    WithdrawalOtpResponse sendOtp(UUID userId);
    List<StudentBankAccountResponse> getSavedBankAccounts(UUID userId);
}
