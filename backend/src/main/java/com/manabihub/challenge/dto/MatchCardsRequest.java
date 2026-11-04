package com.manabihub.challenge.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MatchCardsRequest(@NotNull UUID firstCardId, @NotNull UUID secondCardId) {}
