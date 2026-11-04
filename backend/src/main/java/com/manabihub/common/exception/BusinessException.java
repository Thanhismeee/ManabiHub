package com.manabihub.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for all business-rule violations.
 * <p>
 * Each business exception carries a machine-readable {@code messageCode}
 * that the frontend can use for i18n display mapping, and an optional
 * {@link HttpStatus} to control the HTTP response code (defaults to 400).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String messageCode;
    private final HttpStatus httpStatus;

    public BusinessException(String messageCode, String message) {
        super(message);
        this.messageCode = messageCode;
        this.httpStatus = HttpStatus.BAD_REQUEST;
    }

    public BusinessException(String messageCode, String message, HttpStatus httpStatus) {
        super(message);
        this.messageCode = messageCode;
        this.httpStatus = httpStatus;
    }

    public BusinessException(String messageCode, String message, Throwable cause) {
        super(message, cause);
        this.messageCode = messageCode;
        this.httpStatus = HttpStatus.BAD_REQUEST;
    }

    public BusinessException(String messageCode, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.messageCode = messageCode;
        this.httpStatus = httpStatus;
    }
}
