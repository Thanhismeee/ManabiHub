package com.manabihub.identity.repository;

import com.manabihub.identity.entity.PublicUserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicUserDeviceRepository extends JpaRepository<PublicUserDevice, UUID> {
    
    Optional<PublicUserDevice> findByUserIdAndDeviceKeyHashAndRevokedAtIsNull(UUID userId, String deviceKeyHash);

    List<PublicUserDevice> findByUserIdAndRevokedAtIsNull(UUID userId);

    @Query("SELECT COUNT(d) FROM PublicUserDevice d WHERE d.userId = :userId AND d.revokedAt IS NULL")
    long countActiveDevicesByUserId(UUID userId);
}
