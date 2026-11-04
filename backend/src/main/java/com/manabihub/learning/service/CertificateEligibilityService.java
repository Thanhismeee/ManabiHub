package com.manabihub.learning.service;

import com.manabihub.course.entity.LessonBlock;
import com.manabihub.learning.dto.response.CertificateEligibilityResponse;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.entity.LessonBlockProgress;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CertificateEligibilityService {

    CertificateEligibilityResponse evaluate(
            Enrollment enrollment,
            List<LessonBlock> blocks,
            Map<UUID, LessonBlockProgress> progressByBlockId
    );
}
