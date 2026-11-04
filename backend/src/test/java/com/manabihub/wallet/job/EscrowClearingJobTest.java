package com.manabihub.wallet.job;

import com.manabihub.wallet.entity.EscrowLedger;
import com.manabihub.wallet.repository.EscrowLedgerRepository;
import com.manabihub.wallet.service.EscrowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscrowClearingJobTest {

    @Mock
    private EscrowLedgerRepository escrowLedgerRepository;

    @Mock
    private EscrowService escrowService;

    @Test
    void releaseEligibleEscrows_continuesAfterOneRecordFails() {
        EscrowLedger first = EscrowLedger.builder()
                .id(UUID.randomUUID())
                .createdAt(Instant.now().minusSeconds(120))
                .build();
        EscrowLedger second = EscrowLedger.builder()
                .id(UUID.randomUUID())
                .createdAt(Instant.now().minusSeconds(60))
                .build();
        when(escrowLedgerRepository.findNextEligibleChunk(
                eq(com.manabihub.wallet.enums.EscrowStatus.HELD),
                any(Instant.class),
                any(Instant.class),
                any(UUID.class),
                any()))
                .thenReturn(List.of(first, second), List.of());
        doThrow(new IllegalStateException("simulated isolated record failure"))
                .when(escrowService)
                .processEscrowRelease(first.getId());

        new EscrowClearingJob(escrowLedgerRepository, escrowService).releaseEligibleEscrows();

        verify(escrowService).processEscrowRelease(first.getId());
        verify(escrowService).processEscrowRelease(second.getId());
        verify(escrowLedgerRepository, times(2)).findNextEligibleChunk(
                eq(com.manabihub.wallet.enums.EscrowStatus.HELD),
                any(Instant.class),
                any(Instant.class),
                any(UUID.class),
                any());
    }
}
