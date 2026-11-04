package com.manabihub.identity.service;

import lombok.Getter;

@Getter
public class FirebasePhoneVerificationException extends RuntimeException {

    private final boolean configurationFailure;

    private FirebasePhoneVerificationException(String message, boolean configurationFailure, Throwable cause) {
        super(message, cause);
        this.configurationFailure = configurationFailure;
    }

    public static FirebasePhoneVerificationException invalid(String message, Throwable cause) {
        return new FirebasePhoneVerificationException(message, false, cause);
    }

    public static FirebasePhoneVerificationException unavailable(String message, Throwable cause) {
        return new FirebasePhoneVerificationException(message, true, cause);
    }
}
