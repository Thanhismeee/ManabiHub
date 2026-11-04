package com.manabihub.wallet.mapper;

import com.manabihub.wallet.dto.response.TeacherWalletResponse;
import com.manabihub.wallet.entity.Wallet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WalletMapper {

    @Mapping(target = "walletFrozen", source = "wallet.frozen")
    @Mapping(target = "pendingBalance", constant = "0")
    @Mapping(target = "reservedBalance", source = "wallet.frozenBalance")
    @Mapping(target = "availableBalance", expression = "java(wallet.getAvailableBalance())")
    TeacherWalletResponse toResponse(Wallet wallet, BigDecimal minimumPayoutAmount, int clearingPeriodDays, LocalDate nextPayoutDate);
}
