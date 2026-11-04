package com.manabihub.finaltest.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalTestQuestionDto {

    private UUID id;

    @NotBlank(message = "MSG-FTEST-002")
    private String content;

    @NotBlank(message = "MSG-FTEST-002")
    private String explanation;

    @Valid
    @Size(min = 2, message = "MSG-FTEST-002")
    @Builder.Default
    private List<FinalTestChoiceDto> choices = new ArrayList<>();
}
