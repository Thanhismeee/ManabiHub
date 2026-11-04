package com.manabihub.course.controller;

import com.manabihub.common.response.ApiResponse;
import com.manabihub.course.dto.response.CourseCategoryResponse;
import com.manabihub.course.entity.CourseCategory;
import com.manabihub.course.repository.CourseCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/course-categories")
public class CourseCategoryController {

    private final CourseCategoryRepository courseCategoryRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseCategoryResponse>>> listCategories() {
        List<CourseCategoryResponse> categories = courseCategoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    private CourseCategoryResponse toResponse(CourseCategory category) {
        return new CourseCategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDescription()
        );
    }
}
