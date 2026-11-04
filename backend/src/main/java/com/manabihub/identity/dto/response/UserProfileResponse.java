package com.manabihub.identity.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import java.time.Instant;

@Getter
@Setter
public class UserProfileResponse {

    private UUID id;

    private String email;

    private String fullName;

    private String phoneNumber;

    private boolean phoneVerified;

    private Instant phoneVerifiedAt;

    private String avatarUrl;
}
