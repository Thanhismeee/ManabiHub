package com.manabihub.kyc.port;

import java.time.LocalDate;

public record NationalIdRecordDto(
        String idNumber,
        String fullName,
        LocalDate dateOfBirth
) {}
