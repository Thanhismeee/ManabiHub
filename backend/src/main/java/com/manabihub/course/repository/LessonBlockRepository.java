package com.manabihub.course.repository;

import com.manabihub.course.entity.LessonBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonBlockRepository extends JpaRepository<LessonBlock, UUID> {

    List<LessonBlock> findByModule_IdOrderByOrderIndexAsc(UUID moduleId);

    Optional<LessonBlock> findByIdAndModule_Id(UUID id, UUID moduleId);

    @org.springframework.data.jpa.repository.Query("""
            SELECT block
            FROM LessonBlock block
            JOIN FETCH block.module module
            JOIN FETCH module.course course
            WHERE block.id = :lessonBlockId AND course.id = :courseId
            """)
    Optional<LessonBlock> findByIdAndCourseId(
            @org.springframework.data.repository.query.Param("lessonBlockId") UUID lessonBlockId,
            @org.springframework.data.repository.query.Param("courseId") UUID courseId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
            SELECT block
            FROM LessonBlock block
            JOIN FETCH block.module module
            JOIN FETCH module.course course
            JOIN FETCH course.teacher teacher
            JOIN FETCH teacher.user
            WHERE block.id = :lessonBlockId
            """)
    Optional<LessonBlock> findByIdForModeration(
            @org.springframework.data.repository.query.Param("lessonBlockId") UUID lessonBlockId
    );

    @org.springframework.data.jpa.repository.Query("""
            SELECT block.module.course.id AS courseId, COUNT(block) AS totalCount
            FROM LessonBlock block
            WHERE block.module.course.id IN :courseIds
              AND block.moderationHidden = false
            GROUP BY block.module.course.id
            """)
    List<CourseBlockCount> countByCourseIds(
            @org.springframework.data.repository.query.Param("courseIds") Collection<UUID> courseIds);

    interface CourseBlockCount {
        UUID getCourseId();
        long getTotalCount();
    }

    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(block)
            FROM LessonBlock block
            WHERE block.module.course.id = :courseId
              AND block.moderationHidden = false
            """)
    int countByCourse_Id(@org.springframework.data.repository.query.Param("courseId") UUID courseId);
}
