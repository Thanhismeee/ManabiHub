package com.manabihub.identity.service;

public interface FirebasePhoneIdentityVerifier {
    VerifiedFirebasePhone verify(String idToken);
}
