package com.manabihub.course.service;

import com.manabihub.course.dto.response.ValidationResultResponse;

import java.util.UUID;

public interface CourseValidationService {
    
    /**
     * Validates the course structure and content before it can be submitted for review.
     * @param courseId The UUID of the course draft.
     * @return ValidationResultResponse containing isValid flag and list of ValidationErrors.
     */
    ValidationResultResponse validateCourse(UUID courseId);

    /**
     * Runs the same publication checks for an authorized internal reviewer.
     * Unlike {@link #validateCourse(UUID)}, this method does not require the
     * current user to own the course because authorization is enforced by the
     * Course Manager approval service.
     */
    ValidationResultResponse validateCourseForReview(UUID courseId);
}
