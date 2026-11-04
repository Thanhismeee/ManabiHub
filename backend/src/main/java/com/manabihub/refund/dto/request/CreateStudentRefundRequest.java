package com.manabihub.refund.dto.request;

import com.manabihub.refund.enums.StudentRefundType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateStudentRefundRequest(
        @NotNull(message = "Order item ID is required")
        UUID orderItemId,
        
        @NotNull(message = "Refund type is required")
        StudentRefundType refundType,
        
        @NotBlank(message = "Reason is required")
        @Size(max = 2000, message = "Reason must not exceed 2000 characters")
        String reason
) {}
