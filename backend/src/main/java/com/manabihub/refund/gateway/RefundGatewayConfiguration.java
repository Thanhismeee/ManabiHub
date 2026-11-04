package com.manabihub.refund.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RefundGatewayConfiguration {

    @Bean
    @ConditionalOnMissingBean(RefundGateway.class)
    RefundGateway unavailableRefundGateway() {
        return new UnavailableRefundGateway();
    }
}
