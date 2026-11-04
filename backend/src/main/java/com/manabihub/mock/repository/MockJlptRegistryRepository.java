package com.manabihub.mock.repository;

import com.manabihub.mock.domain.MockJlptRegistryRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MockJlptRegistryRepository extends JpaRepository<MockJlptRegistryRecord, UUID> {
    Optional<MockJlptRegistryRecord> findByRegistrationNumberAndActiveTrue(String registrationNumber);
}
