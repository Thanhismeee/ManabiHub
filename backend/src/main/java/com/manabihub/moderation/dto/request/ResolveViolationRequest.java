package com.manabihub.moderation.dto.request;

import com.manabihub.moderation.enums.ModerationActionType;
import com.manabihub.moderation.enums.ModerationDecisionType;
import com.manabihub.moderation.enums.EvidenceRequestedFrom;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class ResolveViolationRequest {

    @NotNull(message = "{MSG-COM-002}")
    private ModerationDecisionType decision;

    @Size(max = 10, message = "Too many moderation actions")
    private List<@NotNull ModerationActionType> actions;

    @Size(max = 2000, message = "Decision note must not exceed 2000 characters")
    private String decisionNote;

    private EvidenceRequestedFrom evidenceRequestedFrom;

    @Size(max = 100, message = "Too many moderation targets")
    private List<@NotNull UUID> targetIds;
}
