package com.manabihub.systemconfig.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SystemSettingResponse(
        UUID id,
        String key,
        String value,
        String valueType,
        String description,
        boolean editable,
        UUID updatedBy,
        Instant updatedAt
) {
}
