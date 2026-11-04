package com.manabihub.moderation.repository;

import com.manabihub.moderation.entity.ModerationActionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ModerationActionRecordRepository extends JpaRepository<ModerationActionRecord, UUID> {
}
