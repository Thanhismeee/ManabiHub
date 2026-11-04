package com.manabihub.identity.repository;

import com.manabihub.identity.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByPhoneNumber(String phoneNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM IdentityAppUser u WHERE u.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT u.id FROM IdentityAppUser u WHERE lower(u.email) LIKE lower(concat('%', :search, '%')) OR lower(u.fullName) LIKE lower(concat('%', :search, '%'))")
    List<UUID> searchUserIds(@Param("search") String search, org.springframework.data.domain.Pageable pageable);
}
