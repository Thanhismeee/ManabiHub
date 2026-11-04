package com.manabihub.course.dto.response;

import com.manabihub.course.enums.LessonBlockType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PublicLessonBlockResponse {
    private UUID id;
    private String title;
    private LessonBlockType type;
    private Integer durationMinutes;
    private int orderIndex;
}
