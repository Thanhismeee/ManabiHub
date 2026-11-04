package com.manabihub.kyc.dto.response;

import com.manabihub.kyc.domain.KycRequestStatus;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycRequestResponse {
    private UUID id;
    private UUID teacherId;
    private String teacherEmail;
    private String teacherFullName;
    private KycRequestStatus status;
    private String displayName;
    private String idCardFrontUrl;
    private String idCardBackUrl;
    private String certificateUrl;
    private String selfieUrl;
    private Boolean copyrightAccepted;
    private String vnptVerificationStatus;
    private String vnptResponseDetails;
    private String riskLevel;
    private String exceptionStage;
    private String exceptionType;
    private String exceptionReason;
    private String certificateCode;
    private String certificateHolderName;
    private String certificateDateOfBirth;
    private String certificateLevel;
    private String certificateOcrText;
    private String decisionNote;
    private Instant createdAt;
    private Instant updatedAt;
    private String processedByEmail;
    private Instant processedAt;
}
