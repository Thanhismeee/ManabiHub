package com.manabihub.ai.service;

import com.manabihub.course.entity.Course;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.systemconfig.service.SystemSettingValueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCourseEligibilityServiceTest {

    @Mock
    private SystemSettingValueService settingValueService;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private AiCourseEligibilityService service;

    @Test
    void freeCourseIsEligibleWhenAdminPriceFloorIsZero() {
        assertTrue(service.isPriceEligible(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void courseBelowConfiguredFloorIsNotEligible() {
        assertFalse(service.isPriceEligible(new BigDecimal("99999"), new BigDecimal("100000")));
    }

    @Test
    void synchronizingCoursesUpdatesOnlyStaleFlags() {
        Course free = Course.builder()
                .price(BigDecimal.ZERO)
                .aiSupported(false)
                .build();
        Course paid = Course.builder()
                .price(new BigDecimal("250000"))
                .aiSupported(true)
                .build();
        when(courseRepository.findAll()).thenReturn(List.of(free, paid));

        int changed = service.synchronizeAllCourses(BigDecimal.ZERO);

        assertTrue(free.isAiSupported());
        assertTrue(paid.isAiSupported());
        assertTrue(changed == 1);
        verify(courseRepository).saveAll(anyList());
    }
}
