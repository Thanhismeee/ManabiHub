package com.manabihub.mock.dto;

import com.manabihub.mock.domain.MockJlptRegistryRecord;

import java.time.LocalDate;

public record MockJlptRegistryResponse(
        String registrationNumber,
        String fullName,
        LocalDate dateOfBirth,
        String testLevel,
        LocalDate testDate,
        String testSite,
        Integer totalScore,
        String passStatus
) {
    public static MockJlptRegistryResponse fromEntity(MockJlptRegistryRecord record) {
        return new MockJlptRegistryResponse(
                record.getRegistrationNumber(),
                record.getFullName(),
                record.getDateOfBirth(),
                record.getTestLevel(),
                record.getTestDate(),
                record.getTestSite(),
                record.getTotalScore(),
                record.getPassStatus()
        );
    }
}
