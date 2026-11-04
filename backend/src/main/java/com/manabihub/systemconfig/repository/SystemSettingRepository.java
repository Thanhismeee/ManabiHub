package com.manabihub.systemconfig.repository;

import com.manabihub.systemconfig.entity.SystemSetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, UUID> {

    Optional<SystemSetting> findBySettingKey(String settingKey);

    List<SystemSetting> findAllBySettingKeyInOrderBySettingKeyAsc(Collection<String> settingKeys);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select setting
            from SystemSetting setting
            where setting.settingKey in :settingKeys
            order by setting.settingKey
            """)
    List<SystemSetting> findAllBySettingKeyInForUpdate(
            @Param("settingKeys") Collection<String> settingKeys);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select setting from SystemSetting setting where setting.settingKey = :settingKey")
    Optional<SystemSetting> findBySettingKeyForUpdate(@Param("settingKey") String settingKey);
}
