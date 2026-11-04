package com.manabihub.operations.dto;

import java.util.List;

/**
 * Deliberately contains only a fixed component name and booleans. Runtime
 * property names and values are never serialized by this contract.
 */
public record RuntimeConfigurationResponse(
        List<ComponentStatus> components
) {
    public record ComponentStatus(
            String component,
            boolean configured,
            boolean enabled
    ) {
    }
}
