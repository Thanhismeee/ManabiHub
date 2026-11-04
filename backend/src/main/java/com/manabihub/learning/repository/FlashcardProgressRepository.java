package com.manabihub.learning.repository;

import com.manabihub.learning.entity.FlashcardProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlashcardProgressRepository extends JpaRepository<FlashcardProgress, UUID> {
    List<FlashcardProgress> findByEnrollmentId(UUID enrollmentId);

    List<FlashcardProgress> findByEnrollmentIdAndLessonBlockId(UUID enrollmentId, UUID lessonBlockId);

    Optional<FlashcardProgress> findByEnrollmentIdAndLessonBlockIdAndCardIndex(UUID enrollmentId, UUID lessonBlockId, int cardIndex);

    int countByEnrollmentIdAndLessonBlockId(UUID enrollmentId, UUID lessonBlockId);

    @Modifying
    @Query(value = """
        INSERT INTO flashcard_progress (id, enrollment_id, lesson_block_id, card_index, status, created_at, updated_at)
        VALUES (gen_random_uuid(), :enrollmentId, :lessonBlockId, :cardIndex, :#{#status.name()}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (enrollment_id, lesson_block_id, card_index)
        DO UPDATE SET status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP
        """, nativeQuery = true)
    void upsertStatus(
        @Param("enrollmentId") UUID enrollmentId,
        @Param("lessonBlockId") UUID lessonBlockId,
        @Param("cardIndex") int cardIndex,
        @Param("status") com.manabihub.learning.enums.FlashcardStatus status
    );
}
