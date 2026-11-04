package com.manabihub.course.repository;

import com.manabihub.course.entity.CourseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseCategoryRepository extends JpaRepository<CourseCategory, UUID> {

    List<CourseCategory> findByActiveTrueOrderBySortOrderAscNameAsc();

    boolean existsByCodeAndActiveTrue(String code);
}
