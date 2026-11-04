package com.manabihub.course.repository;

import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.enums.JlptLevel;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicCourseSpecificationTest {

    @Mock
    private Root<Course> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Predicate predicate;

    @Mock
    private jakarta.persistence.criteria.Path<Object> path;

    @Test
    void buildSearch_alwaysFiltersByPublishedStatus() {
        when(root.get("status")).thenReturn(path);
        when(cb.equal(any(), eq(CourseStatus.PUBLISHED))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Course> spec = PublicCourseSpecification.buildSearch(
                null, null, null, null, null
        );

        Predicate result = spec.toPredicate(root, query, cb);
        assertNotNull(result);
        verify(cb).equal(any(), eq(CourseStatus.PUBLISHED));
    }

    @Test
    void buildSearch_withKeyword_addsOrPredicate() {
        when(root.get("status")).thenReturn(path);
        when(root.get("title")).thenReturn(path);
        when(root.get("description")).thenReturn(path);
        when(cb.equal(any(), eq(CourseStatus.PUBLISHED))).thenReturn(predicate);
        when(cb.lower(any())).thenReturn(null);
        when(cb.like(any(), eq("%kanji%"))).thenReturn(predicate);
        when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Course> spec = PublicCourseSpecification.buildSearch(
                "kanji", null, null, null, null
        );

        Predicate result = spec.toPredicate(root, query, cb);
        assertNotNull(result);
        verify(cb).or(any(Predicate.class), any(Predicate.class));
    }

    @Test
    void buildSearch_withCategory_addsCategoryPredicate() {
        when(root.get("status")).thenReturn(path);
        when(root.get("category")).thenReturn(path);
        when(cb.equal(any(), eq(CourseStatus.PUBLISHED))).thenReturn(predicate);
        when(cb.equal(any(), eq("grammar"))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Course> spec = PublicCourseSpecification.buildSearch(
                null, "grammar", null, null, null
        );

        Predicate result = spec.toPredicate(root, query, cb);
        assertNotNull(result);
        verify(cb).equal(any(), eq("grammar"));
    }

    @Test
    void buildSearch_withJlptLevel_addsLevelPredicate() {
        when(root.get("status")).thenReturn(path);
        when(root.get("jlptLevel")).thenReturn(path);
        when(cb.equal(any(), eq(CourseStatus.PUBLISHED))).thenReturn(predicate);
        when(cb.equal(any(), eq(JlptLevel.N3))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Course> spec = PublicCourseSpecification.buildSearch(
                null, null, JlptLevel.N3, null, null
        );

        Predicate result = spec.toPredicate(root, query, cb);
        assertNotNull(result);
        verify(cb).equal(any(), eq(JlptLevel.N3));
    }

    @Test
    void buildSearch_withPriceRange_addsPricePredicates() {
        when(root.get("status")).thenReturn(path);
        when(root.get("price")).thenReturn(path);
        when(cb.equal(any(), eq(CourseStatus.PUBLISHED))).thenReturn(predicate);
        when(cb.greaterThanOrEqualTo(any(), eq(new BigDecimal("100000")))).thenReturn(predicate);
        when(cb.lessThanOrEqualTo(any(), eq(new BigDecimal("500000")))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Course> spec = PublicCourseSpecification.buildSearch(
                null, null, null, new BigDecimal("100000"), new BigDecimal("500000")
        );

        Predicate result = spec.toPredicate(root, query, cb);
        assertNotNull(result);
        verify(cb).greaterThanOrEqualTo(any(), eq(new BigDecimal("100000")));
        verify(cb).lessThanOrEqualTo(any(), eq(new BigDecimal("500000")));
    }
}
