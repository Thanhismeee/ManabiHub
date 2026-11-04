package com.manabihub.course.repository.projection;

import java.util.UUID;

/** Visible lesson count for a public course card. */
public interface PublicCourseLessonCountProjection {

    UUID getCourseId();

    long getTotalLessons();
}
