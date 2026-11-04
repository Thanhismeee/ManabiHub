package com.manabihub.identity.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AdminProfileResponse {
    private UUID id;
    private String email;
    private String fullName;
    private String role;
}
