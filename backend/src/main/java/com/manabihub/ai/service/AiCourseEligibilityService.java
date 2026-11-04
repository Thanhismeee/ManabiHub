package com.manabihub.ai.service;

import com.manabihub.course.entity.Course;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.systemconfig.service.SystemSettingValueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Keeps the persisted per-course AI capability in sync with the system price
 * floor. Global AI switches are evaluated at request time; this flag only
 * answers whether the course plan is eligible by price.
 */
@Service
@RequiredArgsConstructor
public class AiCourseEligibilityService {

    private static final String PRICE_FLOOR_SETTING = "AI_SUPPORT_PRICE_FLOOR";
    private static final BigDecimal DEFAULT_PRICE_FLOOR = new BigDecimal("100000");

    private final SystemSettingValueService settingValueService;
    private final CourseRepository courseRepository;

    public boolean isPriceEligible(BigDecimal coursePrice) {
        return isPriceEligible(coursePrice, currentPriceFloor());
    }

    public boolean isPriceEligible(BigDecimal coursePrice, BigDecimal priceFloor) {
        return coursePrice != null
                && priceFloor != null
                && coursePrice.compareTo(priceFloor) >= 0;
    }

    public BigDecimal currentPriceFloor() {
        return settingValueService.getDecimal(PRICE_FLOOR_SETTING, DEFAULT_PRICE_FLOOR);
    }

    /**
     * Recomputes the denormalized flag for existing courses after an admin
     * changes the price floor (or after a deployment repairs legacy data).
     */
    @Transactional
    public int synchronizeAllCourses(BigDecimal priceFloor) {
        List<Course> courses = courseRepository.findAll();
        int changed = 0;
        for (Course course : courses) {
            boolean eligible = isPriceEligible(course.getPrice(), priceFloor);
            if (course.isAiSupported() != eligible) {
                course.setAiSupported(eligible);
                changed++;
            }
        }
        if (changed > 0) {
            courseRepository.saveAll(courses);
        }
        return changed;
    }
}
