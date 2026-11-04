package com.manabihub.course.repository;

import com.manabihub.course.entity.CourseModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseModuleRepository extends JpaRepository<CourseModule, UUID> {

    List<CourseModule> findByCourse_IdOrderByOrderIndexAsc(UUID courseId);

    Optional<CourseModule> findByIdAndCourse_Id(UUID id, UUID courseId);
}
