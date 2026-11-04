package com.manabihub.learning.service;

import com.manabihub.learning.dto.response.LearningCertificateResponse;

import java.util.UUID;

public interface StudentCertificateService {

    LearningCertificateResponse getCertificate(UUID courseId);

    LearningCertificateResponse generateCertificate(UUID courseId);
}
