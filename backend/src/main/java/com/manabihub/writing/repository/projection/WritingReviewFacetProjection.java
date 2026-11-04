package com.manabihub.writing.repository.projection;

import java.util.UUID;

public interface WritingReviewFacetProjection {
    UUID getCourseId();

    String getCourseTitle();

    UUID getLessonId();

    String getLessonTitle();
}
