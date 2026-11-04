package com.manabihub.finaltest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalTestChoiceDto {

    private UUID id;

    @NotBlank(message = "MSG-FTEST-002")
    private String content;

    @NotNull(message = "MSG-FTEST-002")
    @Builder.Default
    private Boolean isCorrect = false;
}
