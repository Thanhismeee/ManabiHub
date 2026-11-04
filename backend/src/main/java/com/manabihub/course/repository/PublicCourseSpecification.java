package com.manabihub.course.repository;

import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.review.entity.CourseReview;
import com.manabihub.review.enums.CourseReviewStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification builder for public course catalog search.
 * <p>
 * The {@code PUBLISHED} status filter is always applied to ensure
 * unpublished courses never leak through the public API, regardless
 * of what the frontend sends.
 */
public final class PublicCourseSpecification {

    private PublicCourseSpecification() {
        // Utility class
    }

    public static Specification<Course> buildSearch(
            String keyword,
            String category,
            JlptLevel jlptLevel,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        return buildSearch(
                keyword, category, null, null, null, jlptLevel,
                minPrice, maxPrice, null, Sort.Direction.DESC
        );
    }

    public static Specification<Course> buildSearch(
            String keyword,
            String category,
            LearningLanguage learningLanguage,
            ProficiencyFramework proficiencyFramework,
            String proficiencyLevel,
            JlptLevel legacyJlptLevel,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String aggregateSort,
            Sort.Direction direction
    ) {
        return buildSearchInternal(
                keyword,
                category,
                learningLanguage,
                proficiencyFramework,
                proficiencyLevel,
                legacyJlptLevel,
                minPrice,
                maxPrice,
                aggregateSort,
                direction
        );
    }

    /**
     * Builds the public search predicate and, when requested, orders by values
     * that are calculated from related tables rather than stored on courses.
     * Keeping this in the specification makes aggregate sorting work together
     * with the database page/size window instead of sorting only the current
     * page in the browser.
     */
    public static Specification<Course> buildSearch(
            String keyword,
            String category,
            JlptLevel jlptLevel,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String aggregateSort,
            Sort.Direction direction
    ) {
        return buildSearchInternal(
                keyword, category, null, null, null, jlptLevel,
                minPrice, maxPrice, aggregateSort, direction
        );
    }

    private static Specification<Course> buildSearchInternal(
            String keyword,
            String category,
            LearningLanguage learningLanguage,
            ProficiencyFramework proficiencyFramework,
            String proficiencyLevel,
            JlptLevel jlptLevel,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String aggregateSort,
            Sort.Direction direction
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter PUBLISHED courses only
            predicates.add(cb.equal(root.get("status"), CourseStatus.PUBLISHED));

            // Keyword search: title OR description (case-insensitive)
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate descriptionMatch = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(titleMatch, descriptionMatch));
            }

            // Category filter (matches Course.category string field)
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category.trim()));
            }

            // JLPT Level filter
            if (jlptLevel != null) {
                predicates.add(cb.equal(root.get("jlptLevel"), jlptLevel));
            }

            if (learningLanguage != null) {
                predicates.add(cb.equal(root.get("learningLanguage"), learningLanguage));
            }
            if (proficiencyFramework != null) {
                predicates.add(cb.equal(root.get("proficiencyFramework"), proficiencyFramework));
            }
            if (proficiencyLevel != null && !proficiencyLevel.isBlank()) {
                predicates.add(cb.equal(
                        cb.upper(root.get("proficiencyLevel")),
                        proficiencyLevel.trim().toUpperCase(java.util.Locale.ROOT)
                ));
            }

            // Price range filters
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            if (!isCountQuery(query) && ("enrollmentCount".equals(aggregateSort)
                    || "averageRating".equals(aggregateSort))) {
                Expression<? extends Number> score = "enrollmentCount".equals(aggregateSort)
                        ? enrollmentCount(root, query, cb)
                        : averageRating(root, query, cb);
                query.orderBy(
                        direction == Sort.Direction.ASC ? cb.asc(score) : cb.desc(score),
                        cb.desc(root.get("publishedAt")),
                        cb.asc(root.get("id"))
                );
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean isCountQuery(jakarta.persistence.criteria.CriteriaQuery<?> query) {
        Class<?> resultType = query.getResultType();
        return resultType == Long.class || resultType == long.class;
    }

    private static Expression<Long> enrollmentCount(
            Root<Course> course,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb
    ) {
        Subquery<Long> count = query.subquery(Long.class);
        Root<Enrollment> enrollment = count.from(Enrollment.class);
        count.select(cb.count(enrollment));
        count.where(
                cb.equal(enrollment.get("course").get("id"), course.get("id")),
                enrollment.get("status").in(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED)
        );
        return count;
    }

    private static Expression<Double> averageRating(
            Root<Course> course,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb
    ) {
        Subquery<Double> average = query.subquery(Double.class);
        Root<CourseReview> review = average.from(CourseReview.class);
        Join<CourseReview, Enrollment> enrollment = review.join("enrollment");
        average.select(cb.avg(review.<Number>get("rating")));
        average.where(
                cb.equal(enrollment.get("course").get("id"), course.get("id")),
                cb.equal(review.get("status"), CourseReviewStatus.APPROVED),
                enrollment.get("status").in(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED)
        );
        jakarta.persistence.criteria.CriteriaBuilder.Coalesce<Double> coalesce = cb.coalesce();
        coalesce.value(average);
        coalesce.value(0d);
        return coalesce;
    }
}
