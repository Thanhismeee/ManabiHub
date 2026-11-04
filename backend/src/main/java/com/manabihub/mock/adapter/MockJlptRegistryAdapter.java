package com.manabihub.mock.adapter;

import com.manabihub.kyc.port.JlptRecordDto;
import com.manabihub.kyc.port.JlptRegistryPort;
import com.manabihub.mock.repository.MockJlptRegistryRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MockJlptRegistryAdapter implements JlptRegistryPort {

    private final MockJlptRegistryRepository repository;

    public MockJlptRegistryAdapter(MockJlptRegistryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<JlptRecordDto> findActiveByRegistrationNumber(String registrationNumber) {
        return repository.findByRegistrationNumberAndActiveTrue(registrationNumber)
                .map(record -> new JlptRecordDto(
                        record.getRegistrationNumber(),
                        record.getFullName(),
                        record.getDateOfBirth(),
                        record.getTestLevel(),
                        record.getTotalScore(),
                        record.getPassStatus()
                ));
    }
}
