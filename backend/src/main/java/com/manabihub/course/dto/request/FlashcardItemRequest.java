package com.manabihub.course.dto.request;

import jakarta.validation.constraints.Size;

public record FlashcardItemRequest(
        @Size(max = 160) String front,
        @Size(max = 500) String back
) {
}
