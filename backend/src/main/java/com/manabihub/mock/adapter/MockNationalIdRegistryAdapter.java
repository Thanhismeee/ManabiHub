package com.manabihub.mock.adapter;

import com.manabihub.kyc.port.NationalIdRecordDto;
import com.manabihub.kyc.port.NationalIdRegistryPort;
import com.manabihub.mock.repository.MockNationalIdRegistryRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MockNationalIdRegistryAdapter implements NationalIdRegistryPort {

    private final MockNationalIdRegistryRepository repository;

    public MockNationalIdRegistryAdapter(MockNationalIdRegistryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<NationalIdRecordDto> findActiveByIdNumber(String idNumber) {
        return repository.findByIdNumberAndActiveTrue(idNumber)
                .map(record -> new NationalIdRecordDto(
                        record.getIdNumber(),
                        record.getFullName(),
                        record.getDateOfBirth()
                ));
    }
}
