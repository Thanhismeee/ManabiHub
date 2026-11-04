package com.manabihub.kyc.port;

import java.time.LocalDate;

public record JlptRecordDto(
        String registrationNumber,
        String fullName,
        LocalDate dateOfBirth,
        String testLevel,
        Integer totalScore,
        String passStatus
) {}
