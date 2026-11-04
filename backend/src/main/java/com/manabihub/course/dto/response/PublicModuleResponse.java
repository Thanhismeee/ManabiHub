package com.manabihub.course.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PublicModuleResponse {
    private UUID id;
    private String title;
    private int orderIndex;
    private List<PublicLessonBlockResponse> blocks;
}
