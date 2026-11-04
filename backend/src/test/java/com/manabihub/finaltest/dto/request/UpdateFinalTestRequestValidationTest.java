package com.manabihub.finaltest.dto.request;

import com.manabihub.course.enums.JlptLevel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateFinalTestRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsScalarValuesOutsideTheEditorContract() {
        UpdateFinalTestRequest request = validRequest();
        request.setTimeLimitMinutes(181);
        request.setPassingScore(0);
        request.setMaxRetakes(11);

        assertThat(propertyPaths(validator.validate(request)))
                .contains("timeLimitMinutes", "passingScore", "maxRetakes");
    }

    @Test
    void acceptsScalarValuesAtTheEditorBoundaries() {
        UpdateFinalTestRequest lowerBoundary = validRequest();
        lowerBoundary.setTimeLimitMinutes(1);
        lowerBoundary.setPassingScore(1);
        lowerBoundary.setMaxRetakes(1);

        UpdateFinalTestRequest upperBoundary = validRequest();
        upperBoundary.setTimeLimitMinutes(180);
        upperBoundary.setPassingScore(100);
        upperBoundary.setMaxRetakes(10);

        assertThat(validator.validate(lowerBoundary)).isEmpty();
        assertThat(validator.validate(upperBoundary)).isEmpty();
    }

    @Test
    void rejectsQuestionWithFewerThanTwoChoices() {
        UpdateFinalTestRequest request = validRequest();
        request.getQuestions().getFirst().setChoices(List.of(
                FinalTestChoiceDto.builder().content("Only answer").isCorrect(true).build()
        ));

        assertThat(propertyPaths(validator.validate(request)))
                .contains("questions[0].choices");
    }

    private static UpdateFinalTestRequest validRequest() {
        List<FinalTestQuestionDto> questions = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            questions.add(FinalTestQuestionDto.builder()
                    .content("Question " + index)
                    .explanation("Explanation " + index)
                    .choices(List.of(
                            FinalTestChoiceDto.builder().content("Correct").isCorrect(true).build(),
                            FinalTestChoiceDto.builder().content("Incorrect").isCorrect(false).build()
                    ))
                    .build());
        }
        return UpdateFinalTestRequest.builder()
                .timeLimitMinutes(60)
                .passingScore(70)
                .maxRetakes(2)
                .jlptLevel(JlptLevel.N3)
                .skillFocus("Tổng hợp")
                .questions(questions)
                .build();
    }

    private static Set<String> propertyPaths(Set<ConstraintViolation<UpdateFinalTestRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
