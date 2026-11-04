package com.manabihub.learning.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.learning.dto.response.WishlistItemResponse;
import com.manabihub.learning.entity.WishlistItem;
import com.manabihub.learning.repository.WishlistItemRepository;
import com.manabihub.learning.service.StudentWishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentWishlistServiceImpl implements StudentWishlistService {

    private static final String UNIQUE_WISHLIST_CONSTRAINT =
            "uq_student_wishlist_student_course";

    private final WishlistItemRepository wishlistRepository;
    private final CourseRepository courseRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CurrentUserService currentUserService;

    @Override
    public List<WishlistItemResponse> getWishlist() {
        StudentProfile student = resolveStudent();
        Map<UUID, Integer> lessonCounts = wishlistRepository
                .countLessonsByStudentWishlist(student.getId())
                .stream()
                .collect(Collectors.toMap(
                        WishlistItemRepository.CourseLessonCount::getCourseId,
                        count -> Math.toIntExact(count.getTotalLessons())
                ));
        return wishlistRepository.findByStudentIdOrderByCreatedAtDesc(student.getId())
                .stream()
                .map(item -> toResponse(
                        item,
                        lessonCounts.getOrDefault(item.getCourse().getId(), 0)
                ))
                .toList();
    }

    @Override
    @Transactional
    public WishlistItemResponse addCourse(UUID courseId) {
        StudentProfile student = resolveStudent();
        Course course = courseRepository.findById(courseId)
                .filter(value -> value.getStatus() == CourseStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COURSE_NOT_FOUND,
                        "Published course was not found.",
                        HttpStatus.NOT_FOUND
                ));
        if (wishlistRepository.existsByStudentIdAndCourseId(student.getId(), courseId)) {
            throw duplicate();
        }

        try {
            WishlistItem item = wishlistRepository.saveAndFlush(
                    WishlistItem.builder()
                            .student(student)
                            .course(course)
                            .build()
            );
            return toResponse(item, countCourseLessons(course));
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateConstraintViolation(exception)) {
                throw duplicate();
            }
            throw exception;
        }
    }

    @Override
    @Transactional
    public void removeCourse(UUID courseId) {
        StudentProfile student = resolveStudent();
        WishlistItem item = wishlistRepository.findByStudentIdAndCourseId(student.getId(), courseId)
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.LEARNING_WISHLIST_ITEM_NOT_FOUND,
                        "Course is not in your wishlist.",
                        HttpStatus.NOT_FOUND
                ));
        wishlistRepository.delete(item);
    }

    private StudentProfile resolveStudent() {
        return studentProfileRepository.findByUser_Id(currentUserService.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.LEARNING_STUDENT_PROFILE_NOT_FOUND,
                        "Student profile was not found.",
                        HttpStatus.FORBIDDEN
                ));
    }

    private BusinessException duplicate() {
        return new BusinessException(
                MessageCodes.LEARNING_WISHLIST_DUPLICATE,
                "Course is already in your wishlist.",
                HttpStatus.CONFLICT
        );
    }

    private boolean isDuplicateConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();
                if (constraintName != null
                        && UNIQUE_WISHLIST_CONSTRAINT.equalsIgnoreCase(constraintName)) {
                    return true;
                }
            }
            if (cause instanceof java.sql.SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())) {
                String message = sqlException.getMessage();
                if (message != null
                        && message.toLowerCase().contains(UNIQUE_WISHLIST_CONSTRAINT)) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private int countCourseLessons(Course course) {
        return course.getModules().stream()
                .mapToInt(module -> module.getBlocks().size())
                .sum();
    }

    private WishlistItemResponse toResponse(WishlistItem item, int totalLessons) {
        Course course = item.getCourse();
        String teacherName = course.getTeacher() != null
                ? course.getTeacher().getDisplayName()
                : null;
        return new WishlistItemResponse(
                item.getId(),
                item.getCreatedAt(),
                course.getId(),
                course.getTitle(),
                course.getSlug(),
                course.getThumbnailUrl(),
                course.getJlptLevel(),
                course.getCategory(),
                course.getPrice(),
                course.getCurrency(),
                teacherName,
                totalLessons
        );
    }
}
