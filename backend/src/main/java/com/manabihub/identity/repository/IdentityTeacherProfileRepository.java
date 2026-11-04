package com.manabihub.identity.repository;

import com.manabihub.kyc.domain.TeacherProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdentityTeacherProfileRepository
        extends JpaRepository<TeacherProfile, UUID> {

    Optional<TeacherProfile> findByUser_Id(UUID userId);

}