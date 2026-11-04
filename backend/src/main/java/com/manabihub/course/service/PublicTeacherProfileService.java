package com.manabihub.course.service;

import com.manabihub.course.dto.response.PublicTeacherProfileResponse;
import com.manabihub.course.dto.response.PublicTeacherSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface PublicTeacherProfileService {

    PublicTeacherProfileResponse getProfile(UUID teacherId);

    List<PublicTeacherSummaryResponse> listFeatured(int limit);
}
