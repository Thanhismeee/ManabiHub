package com.manabihub.course.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Aggregate ranking values calculated by PostgreSQL before pagination.
 *
 * <p>The public catalogue uses this projection for synthetic sorts that are
 * not columns of {@code courses}. Keeping the aggregation in the database is
 * important: sorting a page in Java would only rank that page, not the whole
 * public catalogue.</p>
 */
public interface PublicCourseRankProjection {

    UUID getCourseId();

    long getEnrollmentCount();

    BigDecimal getAverageRating();

    long getReviewCount();
}
