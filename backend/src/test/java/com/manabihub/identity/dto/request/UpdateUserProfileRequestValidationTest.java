package com.manabihub.identity.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateUserProfileRequestValidationTest {

    private static ValidatorFactory validatorFactory;

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptsLocalPhoneNumber() {
        assertTrue(violationMessages("0912345678").isEmpty());
    }

    @Test
    void acceptsInternationalPhoneNumber() {
        assertTrue(violationMessages("+84912345678").isEmpty());
    }

    @Test
    void acceptsNullPhoneNumberFromOnboarding() {
        assertTrue(violationMessages(null).isEmpty());
    }

    @Test
    void acceptsEmptyPhoneNumberFromProfileScreen() {
        assertTrue(violationMessages("").isEmpty());
    }

    @Test
    void rejectsMalformedPhoneNumberWithProfileMessageCode() {
        assertEquals(Set.of("MSG-PRO-002"), violationMessages("0912"));
    }

    private Set<String> violationMessages(String phoneNumber) {
        UpdateStudentProfileRequest request = new UpdateStudentProfileRequest();
        request.setFullName("Nguyen Van A");
        request.setPhoneNumber(phoneNumber);

        return validator.validate(request).stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());
    }
}
