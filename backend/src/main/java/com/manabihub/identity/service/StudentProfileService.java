package com.manabihub.identity.service;

import com.manabihub.identity.dto.request.UpdateStudentProfileRequest;
import com.manabihub.identity.dto.response.StudentProfileResponse;

public interface StudentProfileService {

    StudentProfileResponse getMyProfile();

    StudentProfileResponse updateMyProfile(
            UpdateStudentProfileRequest request
    );

}