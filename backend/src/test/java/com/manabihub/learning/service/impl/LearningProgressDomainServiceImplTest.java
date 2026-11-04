package com.manabihub.learning.service.impl;

import com.manabihub.learning.enums.LessonProgressStatus;
import com.manabihub.course.repository.LessonBlockRepository;
import com.manabihub.learning.repository.LessonBlockProgressRepository;
import com.manabihub.learning.service.LearningProgressDomainService.ProgressResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningProgressDomainServiceImplTest {

    @Mock
    private LessonBlockRepository lessonBlockRepository;

    @Mock
    private LessonBlockProgressRepository lessonBlockProgressRepository;

    @InjectMocks
    private LearningProgressDomainServiceImpl service;

    @Test
    void calculateProgress_whenTotalIsZero_returnsZeroPercent() {
        UUID courseId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();

        when(lessonBlockRepository.countByCourse_Id(courseId)).thenReturn(0);
        when(lessonBlockProgressRepository.countByEnrollmentIdAndStatus(enrollmentId, LessonProgressStatus.COMPLETED)).thenReturn(0);

        ProgressResult result = service.calculateProgress(courseId, enrollmentId);

        assertThat(result.completed()).isEqualTo(0);
        assertThat(result.total()).isEqualTo(0);
        assertThat(result.percent()).isEqualTo(0.0);
    }

    @Test
    void calculateProgress_whenCompletedIsLessThanTotal_calculatesPercent() {
        UUID courseId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();

        when(lessonBlockRepository.countByCourse_Id(courseId)).thenReturn(10);
        when(lessonBlockProgressRepository.countByEnrollmentIdAndStatus(enrollmentId, LessonProgressStatus.COMPLETED)).thenReturn(3);

        ProgressResult result = service.calculateProgress(courseId, enrollmentId);

        assertThat(result.completed()).isEqualTo(3);
        assertThat(result.total()).isEqualTo(10);
        assertThat(result.percent()).isEqualTo(30.0);
    }

    @Test
    void calculateProgress_whenCompletedIsMoreThanTotal_capsAt100Percent() {
        UUID courseId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();

        when(lessonBlockRepository.countByCourse_Id(courseId)).thenReturn(10);
        when(lessonBlockProgressRepository.countByEnrollmentIdAndStatus(enrollmentId, LessonProgressStatus.COMPLETED)).thenReturn(12);

        ProgressResult result = service.calculateProgress(courseId, enrollmentId);

        assertThat(result.completed()).isEqualTo(12);
        assertThat(result.total()).isEqualTo(10);
        assertThat(result.percent()).isEqualTo(100.0);
    }
}
