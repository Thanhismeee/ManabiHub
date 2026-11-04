package com.manabihub.learning.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SaveVideoProgressRequest(
        @NotNull @Min(0) Integer positionSeconds,
        @Min(0) Integer watchedSeconds,
        @Min(1) Integer mediaDurationSeconds
) {
    public SaveVideoProgressRequest(Integer positionSeconds) {
        this(positionSeconds, null, null);
    }

    public SaveVideoProgressRequest(Integer positionSeconds, Integer watchedSeconds) {
        this(positionSeconds, watchedSeconds, null);
    }
}
