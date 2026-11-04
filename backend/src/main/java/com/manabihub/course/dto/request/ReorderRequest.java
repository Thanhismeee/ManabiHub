package com.manabihub.course.dto.request;

import java.util.List;
import java.util.UUID;

public record ReorderRequest(
        List<UUID> orderedIds
) {
}
