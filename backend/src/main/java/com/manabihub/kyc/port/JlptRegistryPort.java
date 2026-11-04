package com.manabihub.kyc.port;

import java.util.Optional;

public interface JlptRegistryPort {
    Optional<JlptRecordDto> findActiveByRegistrationNumber(String registrationNumber);
}
