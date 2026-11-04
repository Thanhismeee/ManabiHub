package com.manabihub.common.exception;

import com.manabihub.course.dto.response.ValidationError;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Exception used when a domain object fails complex business validation rules.
 * Carries a list of specific {@link ValidationError} to return to the client.
 */
@Getter
public class ValidationBusinessException extends BusinessException {

    private final List<ValidationError> validationErrors;

    public ValidationBusinessException(String messageCode, String message, List<ValidationError> validationErrors) {
        super(messageCode, message, HttpStatus.BAD_REQUEST);
        this.validationErrors = validationErrors;
    }
}
