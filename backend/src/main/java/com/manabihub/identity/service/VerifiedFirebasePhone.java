package com.manabihub.identity.service;

import java.time.Instant;

public record VerifiedFirebasePhone(
        String phoneNumber,
        Instant authenticatedAt,
        String firebaseUid
) {
}
