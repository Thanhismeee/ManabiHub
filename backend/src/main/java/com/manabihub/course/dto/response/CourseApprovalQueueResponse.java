package com.manabihub.course.dto.response;

import com.manabihub.course.enums.CourseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseApprovalQueueResponse {
    private UUID id;
    private String courseName;
    private String teacherName;
    private String teacherEmail;
    private Instant submittedAt;
    private CourseStatus status;
}
