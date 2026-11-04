package com.manabihub.mock.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.mock.domain.MockNationalIdRegistryRecord;
import com.manabihub.mock.dto.MockNationalIdRegistryResponse;
import com.manabihub.mock.repository.MockNationalIdRegistryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mock/national-id-registry")
@Profile("local")
public class MockNationalIdRegistryController {

    private final MockNationalIdRegistryRepository repository;

    @GetMapping("/{idNumber}")
    public ResponseEntity<ApiResponse<MockNationalIdRegistryResponse>> findByIdNumber(
            @PathVariable String idNumber
    ) {
        MockNationalIdRegistryRecord record = repository.findByIdNumberAndActiveTrue(idNumber)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "Mock National ID registry record was not found",
                        HttpStatus.NOT_FOUND
                ));

        return ResponseEntity.ok(ApiResponse.success(toResponse(record)));
    }

    private MockNationalIdRegistryResponse toResponse(MockNationalIdRegistryRecord record) {
        return new MockNationalIdRegistryResponse(
                record.getId(),
                record.getIdNumber(),
                record.getFullName(),
                record.getDateOfBirth(),
                record.getGender(),
                record.getPermanentAddress(),
                record.getIssueDate(),
                record.getExpiryDate(),
                record.getIssuePlace(),
                record.getDocumentStatus(),
                record.getFrontBackMatchStatus(),
                record.getCornerBlurStatus(),
                record.getIdQualityStatus(),
                record.getIssueDateStatus(),
                record.getExpiryStatus(),
                record.getDocumentIdentificationStatus(),
                record.getWarningStatus(),
                record.getOverlayImageStatus(),
                record.getOpenEyesStatus(),
                record.getBlurredFaceStatus(),
                record.getFaceValidationStatus(),
                record.getCoveredFaceStatus(),
                record.getFaceMatchingScore(),
                record.getSourceProvider(),
                record.getSourceReference(),
                record.getRawPayload()
        );
    }
}
