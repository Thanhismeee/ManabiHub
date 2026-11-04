package com.manabihub.kyc.config;

import com.manabihub.kyc.port.VnptVerificationPort;
import com.manabihub.kyc.service.impl.FailClosedVnptVerificationAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class VnptVerificationConfig {

    @Bean
    @ConditionalOnMissingBean
    public VnptVerificationPort vnptVerificationPort() {
        return new FailClosedVnptVerificationAdapter();
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
