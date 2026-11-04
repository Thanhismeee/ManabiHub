package com.manabihub.identity.service;

import com.manabihub.identity.dto.request.LoginRequest;
import com.manabihub.identity.dto.response.AdminProfileResponse;

import java.util.UUID;

public interface AdminAuthService {
    AdminSessionBundle login(LoginRequest request, String ipAddress, String userAgent);
    AdminProfileResponse getMe(UUID adminId);
}
