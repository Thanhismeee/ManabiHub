package com.manabihub.learning.repository;

import com.manabihub.learning.entity.WishlistItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, UUID> {

    boolean existsByStudentIdAndCourseId(UUID studentId, UUID courseId);

    Optional<WishlistItem> findByStudentIdAndCourseId(UUID studentId, UUID courseId);

    @EntityGraph(attributePaths = {
            "course",
            "course.teacher",
            "course.teacher.user"
    })
    List<WishlistItem> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    @Query(value = """
            SELECT wishlist.course_id AS courseId, COUNT(block.id) AS totalLessons
            FROM student_wishlist wishlist
            LEFT JOIN course_modules module ON module.course_id = wishlist.course_id
            LEFT JOIN course_lesson_blocks block ON block.module_id = module.id
            WHERE wishlist.student_id = :studentId
            GROUP BY wishlist.course_id
            """, nativeQuery = true)
    List<CourseLessonCount> countLessonsByStudentWishlist(@Param("studentId") UUID studentId);

    interface CourseLessonCount {
        UUID getCourseId();

        long getTotalLessons();
    }
}
