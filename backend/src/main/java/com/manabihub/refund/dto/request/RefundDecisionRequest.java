package com.manabihub.refund.dto.request;

import com.manabihub.refund.enums.RefundDecisionReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundDecisionRequest {

    @NotNull(message = "MSG-COM-002")
    private RefundDecisionReason reasonCode;

    @NotBlank(message = "MSG-COM-002")
    @Size(max = 2000, message = "Decision note must not exceed 2000 characters")
    private String note;
}
