package com.manabihub.learning.dto.request;

import com.manabihub.learning.enums.FlashcardStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ReviewFlashcardRequest(
        @NotNull
        @PositiveOrZero
        Integer cardIndex,

        @NotNull
        FlashcardStatus status
) {
}
