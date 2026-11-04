package com.manabihub.identity.service;

import com.manabihub.identity.dto.request.UpdateTeacherProfileRequest;
import com.manabihub.identity.dto.response.TeacherProfileResponse;

public interface TeacherProfileService {

    TeacherProfileResponse getMyProfile();

    TeacherProfileResponse updateMyProfile(
            UpdateTeacherProfileRequest request
    );

}