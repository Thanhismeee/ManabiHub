package com.manabihub.wallet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Legacy validation bounds retained for compatibility with historical top-up order tests.
 * No student-facing endpoint creates new top-up orders.
 */
@Component
@ConfigurationProperties(prefix = "manabihub.wallet")
@Getter
@Setter
public class WalletPaymentProperties {

    private BigDecimal topUpMinAmount = new BigDecimal("10000");
    private BigDecimal topUpMaxAmount = new BigDecimal("100000000");
}
