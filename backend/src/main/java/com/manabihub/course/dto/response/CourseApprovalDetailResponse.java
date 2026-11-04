package com.manabihub.course.dto.response;

import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.finaltest.dto.response.FinalTestResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseApprovalDetailResponse {
    private UUID id;
    private String courseName;
    private String teacherName;
    private String teacherEmail;
    private Instant submittedAt;
    private CourseStatus status;
    private String curriculumSummary;
    private String introduction;
    private JlptLevel jlptLevel;
    private String category;
    private String thumbnailUrl;
    private String outcomes;
    private BigDecimal price;
    private String currency;
    private String prerequisites;
    private String targetStudents;
    private int moduleCount;
    private int lessonBlocksCount;
    private int totalVideoDurationMinutes;
    private boolean finalTestIncluded;
    private String policyEvidence;
    private String previousDecisionReason;
    private String teacherKycStatus;
    private boolean teacherCanPublish;
    private boolean approvalReady;

    @Builder.Default
    private List<String> learningGoals = new ArrayList<>();

    @Builder.Default
    private List<CourseModuleResponse> modules = new ArrayList<>();

    private FinalTestResponse finalTest;

    @Builder.Default
    private List<ValidationError> validationErrors = new ArrayList<>();

    @Builder.Default
    private List<CourseApprovalCriterionResponse> reviewCriteria = new ArrayList<>();
}
