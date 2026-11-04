package com.manabihub.kyc.port;

import java.util.Optional;

public interface NationalIdRegistryPort {
    Optional<NationalIdRecordDto> findActiveByIdNumber(String idNumber);
}
