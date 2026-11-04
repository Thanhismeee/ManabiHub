package com.manabihub.refund.job;

import com.manabihub.refund.repository.RefundRequestRepository;
import com.manabihub.refund.service.impl.AutomaticRefundTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "manabihub.jobs.automatic-refund.enabled", havingValue = "true", matchIfMissing = true)
public class AutomaticRefundJob {
    private final RefundRequestRepository repository;
    private final AutomaticRefundTransactionService transactions;

    @Scheduled(fixedDelayString = "${manabihub.jobs.automatic-refund.delay-ms:2000}", scheduler = "automaticRefundScheduler")
    public void settleDueRefunds() {
        // Bounded batches allow other scheduled jobs to run during an outage.
        for (var refund : repository.findAutomaticRefundsDue(Instant.now(), PageRequest.of(0, 10))) {
            try {
                transactions.execute(refund.getId(), refund.getAutoRefundNextAttemptAt());
            } catch (RuntimeException failure) {
                log.error("Automatic wallet refund failed: refundId={}, scheduledAt={}",
                        refund.getId(), refund.getAutoRefundNextAttemptAt(), failure);
                try {
                    transactions.recordFailure(refund.getId(), refund.getAutoRefundNextAttemptAt(), failure);
                } catch (RuntimeException recordingFailure) {
                    // The durable item remains due if the database is unavailable.
                    log.error("Unable to persist automatic refund failure: refundId={}",
                            refund.getId(), recordingFailure);
                }
            }
        }
    }
}
