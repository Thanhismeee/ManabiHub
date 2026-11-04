package com.manabihub.kyc.dto.request;

import com.manabihub.kyc.domain.KycRequestStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycReviewRequest {

    @NotNull(message = "Decision status is required")
    private KycRequestStatus status;

    private String decisionNote;

    /**
     * Required only when an approved teacher is suspended. The referenced
     * violation report must be resolved with a BAN moderation decision.
     */
    private UUID trustCaseId;

    public KycReviewRequest(KycRequestStatus status, String decisionNote) {
        this.status = status;
        this.decisionNote = decisionNote;
    }
}
