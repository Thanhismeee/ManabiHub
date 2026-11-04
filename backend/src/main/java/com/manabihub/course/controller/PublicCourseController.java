package com.manabihub.course.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.common.response.PageResponse;
import com.manabihub.course.dto.response.PublicCourseDetailResponse;
import com.manabihub.course.dto.response.PublicCourseSummaryResponse;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import com.manabihub.course.service.CourseService;
import com.manabihub.review.dto.response.CourseReviewResponse;
import com.manabihub.review.service.CourseReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/public/courses")
@RequiredArgsConstructor
@Validated
public class PublicCourseController {

    private final CourseService courseService;
    private final CourseReviewService courseReviewService;

    @GetMapping
    public ApiResponse<PageResponse<PublicCourseSummaryResponse>> searchCourses(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) LearningLanguage learningLanguage,
            @RequestParam(required = false) ProficiencyFramework proficiencyFramework,
            @RequestParam(required = false) String proficiencyLevel,
            @RequestParam(required = false) JlptLevel jlptLevel,
            @RequestParam(required = false) @Min(value = 0, message = "Min price must be non-negative") BigDecimal minPrice,
            @RequestParam(required = false) @Min(value = 0, message = "Max price must be non-negative") BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page index must not be less than zero") int page,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "Page size must not be less than one") @Max(value = 50, message = "Page size must not be greater than 50") int size,
            @RequestParam(defaultValue = "publishedAt,desc") String sort
    ) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new com.manabihub.common.exception.BusinessException(
                    com.manabihub.common.constants.MessageCodes.COMMON_BAD_REQUEST,
                    "minPrice cannot be greater than maxPrice",
                    org.springframework.http.HttpStatus.BAD_REQUEST
            );
        }

        Pageable pageable = buildPageable(page, size, sort);
        Page<PublicCourseSummaryResponse> result = courseService.searchPublicCourses(
                keyword,
                category,
                learningLanguage,
                proficiencyFramework,
                proficiencyLevel,
                jlptLevel,
                minPrice,
                maxPrice,
                pageable
        );
        return ApiResponse.success(MessageCodes.COMMON_SUCCESS, "Success", PageResponse.from(result));
    }

    @GetMapping("/{identifier}")
    public ApiResponse<PublicCourseDetailResponse> getCourseDetail(@PathVariable String identifier) {
        PublicCourseDetailResponse data = courseService.getPublicCourseDetail(identifier);
        return ApiResponse.success(MessageCodes.COMMON_SUCCESS, "Success", data);
    }

    @GetMapping("/{identifier}/reviews")
    public ApiResponse<PageResponse<CourseReviewResponse>> getCourseReviews(
            @PathVariable String identifier,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page index must not be less than zero")
            int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Page size must not be less than one")
            @Max(value = 20, message = "Page size must not be greater than twenty")
            int size
    ) {
        Page<CourseReviewResponse> result = courseReviewService.getPublicReviews(
                identifier,
                PageRequest.of(page, size)
        );
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Course reviews loaded.",
                PageResponse.from(result)
        );
    }

    private Pageable buildPageable(int page, int size, String sort) {
        String[] parts = sort == null ? new String[0] : sort.trim().split(",", -1);
        String property = parts.length == 0 ? "publishedAt" : parts[0].trim();
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        // Only allow sorting by safe fields
        if (!isSortableField(property)) {
            property = "publishedAt";
            direction = Sort.Direction.DESC;
        }

        // These are aggregate ranking modes rather than writable entity
        // properties. They intentionally expose descending leaderboards only;
        // the repository maps each one to fixed SQL instead of interpolating a
        // request-controlled ORDER BY expression.
        if (isAggregateRanking(property)) {
            direction = Sort.Direction.DESC;
        }

        return PageRequest.of(page, size, Sort.by(direction, property));
    }

    private boolean isSortableField(String field) {
        return "publishedAt".equals(field)
                || "price".equals(field)
                || "title".equals(field)
                || "createdAt".equals(field)
                || isAggregateRanking(field);
    }

    private boolean isAggregateRanking(String field) {
        return "enrollmentCount".equals(field)
                || "averageRating".equals(field);
    }
}
