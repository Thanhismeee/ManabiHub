package com.manabihub.identity.service;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.AccountIdentityVerification;
import com.manabihub.identity.repository.AccountIdentityVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountIdentityVerificationService {

    private final AccountIdentityVerificationRepository repository;

    @Transactional(readOnly = true)
    public Optional<AccountIdentityVerification> findVerified(UUID userId) {
        return repository.findById(userId);
    }

    @Transactional
    public AccountIdentityVerification recordVerified(
            UUID userId,
            String identityFingerprint,
            String provider,
            String fullName,
            LocalDate dateOfBirth,
            Instant verifiedAt,
            String sourceSubject
    ) {
        if (userId == null || !StringUtils.hasText(identityFingerprint)
                || !StringUtils.hasText(provider) || verifiedAt == null
                || !("STUDENT".equals(sourceSubject) || "TEACHER".equals(sourceSubject))) {
            throw new IllegalArgumentException("A complete account identity verification is required");
        }

        AccountIdentityVerification existingForUser = repository.findById(userId).orElse(null);
        if (existingForUser != null) {
            if (!identityFingerprint.equals(existingForUser.getIdentityFingerprint())) {
                throw identityConflict("A verified CCCD cannot be replaced on this account");
            }
            return existingForUser;
        }

        repository.findByIdentityFingerprint(identityFingerprint).ifPresent(existing -> {
            if (!userId.equals(existing.getUserId())) {
                throw identityConflict("This CCCD is already linked to another account");
            }
        });

        Instant now = Instant.now();
        AccountIdentityVerification verification = new AccountIdentityVerification();
        verification.setUserId(userId);
        verification.setIdentityFingerprint(identityFingerprint);
        verification.setProvider(provider.trim());
        verification.setFullName(StringUtils.hasText(fullName) ? fullName.trim() : null);
        verification.setDateOfBirth(dateOfBirth);
        verification.setVerifiedAt(verifiedAt);
        verification.setSourceSubject(sourceSubject);
        verification.setCreatedAt(now);
        verification.setUpdatedAt(now);
        try {
            return repository.saveAndFlush(verification);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    MessageCodes.MSG_KYC_008,
                    "This CCCD is already linked to another account",
                    HttpStatus.CONFLICT,
                    exception);
        }
    }

    private BusinessException identityConflict(String message) {
        return new BusinessException(MessageCodes.MSG_KYC_008, message, HttpStatus.CONFLICT);
    }
}
