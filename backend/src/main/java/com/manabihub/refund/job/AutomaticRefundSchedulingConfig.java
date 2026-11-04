package com.manabihub.refund.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConditionalOnProperty(name = "manabihub.jobs.automatic-refund.enabled", havingValue = "true", matchIfMissing = true)
public class AutomaticRefundSchedulingConfig {
    @Bean
    public ThreadPoolTaskScheduler automaticRefundScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("automatic-refund-");
        return scheduler;
    }
}
