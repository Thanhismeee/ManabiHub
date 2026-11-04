package com.manabihub.course.repository;

import com.manabihub.course.entity.CourseEditDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourseEditDraftRepository extends JpaRepository<CourseEditDraft, UUID> {
}
