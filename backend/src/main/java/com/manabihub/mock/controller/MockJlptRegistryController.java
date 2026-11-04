package com.manabihub.mock.controller;

import com.manabihub.mock.dto.MockJlptRegistryResponse;
import com.manabihub.mock.repository.MockJlptRegistryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mock/jlpt-registry")
@RequiredArgsConstructor
@Profile("local")
public class MockJlptRegistryController {

    private final MockJlptRegistryRepository repository;

    @GetMapping("/{registrationNumber}")
    public ResponseEntity<MockJlptRegistryResponse> lookupCertificate(@PathVariable String registrationNumber) {
        return repository.findByRegistrationNumberAndActiveTrue(registrationNumber)
                .map(MockJlptRegistryResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
