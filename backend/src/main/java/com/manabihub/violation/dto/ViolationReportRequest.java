package com.manabihub.violation.dto;

import com.manabihub.violation.enums.ViolationTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class ViolationReportRequest {
    @NotNull(message = "Target type is required")
    private ViolationTargetType targetType;

    @NotNull(message = "Target ID is required")
    private UUID targetId;

    @NotBlank(message = "Reason is required")
    @Size(max = 255, message = "Reason must not exceed 255 characters")
    private String reason;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 2000, message = "Description must be between 10 and 2000 characters")
    private String description;
}
