package com.manabihub.review.service;

import com.manabihub.review.dto.request.UpsertCourseReviewRequest;
import com.manabihub.review.dto.request.TeacherCourseReviewReplyRequest;
import com.manabihub.review.dto.response.CourseReviewAggregateResponse;
import com.manabihub.review.dto.response.CourseReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface CourseReviewService {

    CourseReviewResponse getMyReview(UUID courseId);

    CourseReviewResponse upsertMyReview(UUID courseId, UpsertCourseReviewRequest request);

    CourseReviewResponse replyToReview(UUID reviewId, TeacherCourseReviewReplyRequest request);

    Page<CourseReviewResponse> getPublicReviews(String courseIdentifier, Pageable pageable);

    CourseReviewAggregateResponse getAggregate(UUID courseId);

    Map<UUID, CourseReviewAggregateResponse> getAggregates(Collection<UUID> courseIds);
}
