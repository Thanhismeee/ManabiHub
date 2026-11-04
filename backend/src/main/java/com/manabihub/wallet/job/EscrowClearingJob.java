package com.manabihub.wallet.job;

import com.manabihub.wallet.entity.EscrowLedger;
import com.manabihub.wallet.enums.EscrowStatus;
import com.manabihub.wallet.repository.EscrowLedgerRepository;
import com.manabihub.wallet.service.EscrowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EscrowClearingJob {

    private final EscrowLedgerRepository escrowLedgerRepository;
    private final EscrowService escrowService;

    @Scheduled(
            cron = "${manabihub.jobs.escrow-clearing.cron:0 0 0 * * ?}",
            zone = "${manabihub.jobs.escrow-clearing.timezone:UTC}"
    )
    public void releaseEligibleEscrows() {
        log.info("Starting Escrow Clearing Job");
        Instant now = Instant.now();
        int pageSize = 100;

        Instant lastCreatedAt = Instant.EPOCH;
        UUID lastId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        while (true) {
            Pageable pageable = PageRequest.of(0, pageSize);
            List<EscrowLedger> chunk = escrowLedgerRepository.findNextEligibleChunk(
                    EscrowStatus.HELD, now, lastCreatedAt, lastId, pageable);

            if (chunk.isEmpty()) {
                break;
            }

            for (EscrowLedger escrow : chunk) {
                try {
                    escrowService.processEscrowRelease(escrow.getId());
                } catch (Exception e) {
                    log.error("Failed to release escrow {}", escrow.getId(), e);
                }
                lastCreatedAt = escrow.getCreatedAt();
                lastId = escrow.getId();
            }
        }
        
        log.info("Completed Escrow Clearing Job");
    }
}
