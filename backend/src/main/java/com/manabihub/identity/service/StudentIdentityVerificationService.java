package com.manabihub.identity.service;

import com.manabihub.identity.dto.request.StudentIdentityVerificationRequest;
import com.manabihub.identity.dto.response.StudentIdentityVerificationResponse;

public interface StudentIdentityVerificationService {

    StudentIdentityVerificationResponse getStatus();

    StudentIdentityVerificationResponse verify(StudentIdentityVerificationRequest request);
}
