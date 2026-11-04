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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentWishlistServiceImplTest {

    @Mock
    private WishlistItemRepository wishlistRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private StudentWishlistServiceImpl service;

    private UUID userId;
    private StudentProfile student;
    private Course course;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        student = StudentProfile.builder().id(UUID.randomUUID()).build();
        course = Course.builder()
                .id(UUID.randomUUID())
                .title("N4 Foundation")
                .slug("n4-foundation")
                .price(BigDecimal.ZERO)
                .currency("VND")
                .status(CourseStatus.PUBLISHED)
                .modules(new ArrayList<>())
                .build();
    }

    @Test
    void getWishlist_returnsOnlyCurrentStudentItems() {
        mockStudent();
        WishlistItem item = item();
        WishlistItemRepository.CourseLessonCount lessonCount =
                mock(WishlistItemRepository.CourseLessonCount.class);
        when(lessonCount.getCourseId()).thenReturn(course.getId());
        when(lessonCount.getTotalLessons()).thenReturn(12L);
        when(wishlistRepository.countLessonsByStudentWishlist(student.getId()))
                .thenReturn(List.of(lessonCount));
        when(wishlistRepository.findByStudentIdOrderByCreatedAtDesc(student.getId()))
                .thenReturn(List.of(item));

        List<WishlistItemResponse> result = service.getWishlist();

        assertEquals(1, result.size());
        assertEquals(course.getId(), result.getFirst().courseId());
        assertEquals(12, result.getFirst().totalLessons());
        verify(wishlistRepository).findByStudentIdOrderByCreatedAtDesc(student.getId());
    }

    @Test
    void addCourse_addsPublishedCourse() {
        mockStudent();
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(wishlistRepository.existsByStudentIdAndCourseId(student.getId(), course.getId()))
                .thenReturn(false);
        when(wishlistRepository.saveAndFlush(any())).thenReturn(item());

        WishlistItemResponse result = service.addCourse(course.getId());

        assertEquals(course.getId(), result.courseId());
        verify(wishlistRepository).saveAndFlush(any(WishlistItem.class));
    }

    @Test
    void addCourse_blocksExistingDuplicate() {
        mockStudent();
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(wishlistRepository.existsByStudentIdAndCourseId(student.getId(), course.getId()))
                .thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.addCourse(course.getId())
        );

        assertEquals(MessageCodes.LEARNING_WISHLIST_DUPLICATE, exception.getMessageCode());
        verify(wishlistRepository, never()).saveAndFlush(any());
    }

    @Test
    void addCourse_mapsDatabaseRaceToDuplicateConflict() {
        mockStudent();
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(wishlistRepository.existsByStudentIdAndCourseId(student.getId(), course.getId()))
                .thenReturn(false);
        when(wishlistRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "unique",
                        new SQLException(
                                "violates uq_student_wishlist_student_course",
                                "23505"
                        )
                ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.addCourse(course.getId())
        );

        assertEquals(MessageCodes.LEARNING_WISHLIST_DUPLICATE, exception.getMessageCode());
    }

    @Test
    void addCourse_doesNotMaskUnrelatedDatabaseFailure() {
        mockStudent();
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(wishlistRepository.existsByStudentIdAndCourseId(student.getId(), course.getId()))
                .thenReturn(false);
        DataIntegrityViolationException databaseFailure =
                new DataIntegrityViolationException("foreign key failure");
        when(wishlistRepository.saveAndFlush(any())).thenThrow(databaseFailure);

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> service.addCourse(course.getId())
        );

        assertEquals(databaseFailure, exception);
    }

    @Test
    void addCourse_rejectsUnpublishedCourseAsNotFound() {
        mockStudent();
        course.setStatus(CourseStatus.DRAFT);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.addCourse(course.getId())
        );

        assertEquals(MessageCodes.COURSE_NOT_FOUND, exception.getMessageCode());
    }

    @Test
    void removeCourse_deletesOnlyOwnedItem() {
        mockStudent();
        WishlistItem item = item();
        when(wishlistRepository.findByStudentIdAndCourseId(student.getId(), course.getId()))
                .thenReturn(Optional.of(item));

        service.removeCourse(course.getId());

        verify(wishlistRepository).delete(item);
    }

    @Test
    void removeCourse_doesNotDeleteAnotherStudentsItem() {
        mockStudent();
        when(wishlistRepository.findByStudentIdAndCourseId(student.getId(), course.getId()))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.removeCourse(course.getId())
        );

        assertEquals(MessageCodes.LEARNING_WISHLIST_ITEM_NOT_FOUND, exception.getMessageCode());
        verify(wishlistRepository, never()).delete(any());
    }

    @Test
    void wishlistRequiresStudentProfile() {
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, service::getWishlist);

        assertEquals(MessageCodes.LEARNING_STUDENT_PROFILE_NOT_FOUND, exception.getMessageCode());
    }

    private void mockStudent() {
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
    }

    private WishlistItem item() {
        return WishlistItem.builder()
                .id(UUID.randomUUID())
                .student(student)
                .course(course)
                .createdAt(Instant.parse("2026-07-24T00:00:00Z"))
                .build();
    }
}
