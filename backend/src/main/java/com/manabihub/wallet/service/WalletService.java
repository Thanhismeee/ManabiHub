package com.manabihub.wallet.service;

import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.wallet.dto.response.TeacherWalletResponse;
import com.manabihub.wallet.entity.Wallet;
import com.manabihub.wallet.entity.WalletTransaction;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletService {
    TeacherWalletResponse getTeacherWalletByUserId(UUID userId);

    Wallet getOrCreatePlatformWallet();

    Wallet getOrCreateTeacherWallet(TeacherProfile teacher);

    WalletTransaction holdEscrow(TeacherProfile teacher, BigDecimal amount,
                                 String referenceType, UUID referenceId, String note);

    WalletTransaction releaseEscrow(TeacherProfile teacher, BigDecimal amount,
                                    String referenceType, UUID referenceId, String note);
    WalletTransaction refundHeldEscrow(TeacherProfile teacher, BigDecimal amount,
                                       String referenceType, UUID referenceId, String note);
    
    /**
     * Reserves balance for withdrawal.
     * Must be called within a transactional context from the payout module.
     *
     * @param teacherId teacher ID
     * @param amount amount to reserve
     * @param withdrawalId reference ID for the ledger
     */
    void reserveBalance(String teacherId, BigDecimal amount, String withdrawalId);
    
    /**
     * Releases reserved balance back to available balance upon cancellation.
     * Must be called within a transactional context.
     *
     * @param teacherId teacher ID
     * @param amount amount to release
     * @param withdrawalId reference ID for the ledger
     */
    void releaseBalance(String teacherId, BigDecimal amount, String withdrawalId);

}
