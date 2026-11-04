package com.manabihub.mock.repository;

import com.manabihub.mock.domain.MockNationalIdRegistryRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MockNationalIdRegistryRepository extends JpaRepository<MockNationalIdRegistryRecord, UUID> {

    Optional<MockNationalIdRegistryRecord> findByIdNumberAndActiveTrue(String idNumber);
}
