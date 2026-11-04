package com.manabihub.payout.service;

import com.manabihub.payout.dto.request.CreateWithdrawalRequest;
import com.manabihub.payout.dto.response.WithdrawalRequestResponse;
import com.manabihub.payout.dto.response.WithdrawalOtpResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WithdrawalService {
    WithdrawalRequestResponse createWithdrawalRequest(String teacherId, CreateWithdrawalRequest request);
    Page<WithdrawalRequestResponse> getTeacherWithdrawals(String teacherId, Pageable pageable);
    WithdrawalRequestResponse getWithdrawalDetail(String teacherId, String withdrawalId);
    void cancelWithdrawal(String teacherId, String withdrawalId);
    WithdrawalOtpResponse sendWithdrawalOtp(String teacherId);
    java.util.List<com.manabihub.payout.dto.response.TeacherBankAccountResponse> getSavedBankAccounts(String teacherId);
}
