package com.manabihub.finaltest.service;

import com.manabihub.finaltest.dto.request.UpdateFinalTestRequest;
import com.manabihub.finaltest.dto.response.FinalTestResponse;

import java.util.UUID;

public interface FinalTestService {
    FinalTestResponse getFinalTest(UUID courseId);
    FinalTestResponse updateFinalTest(UUID courseId, UpdateFinalTestRequest request);
}
