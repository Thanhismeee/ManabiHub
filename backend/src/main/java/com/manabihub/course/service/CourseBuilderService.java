package com.manabihub.course.service;

import com.manabihub.course.dto.request.CourseModuleRequest;
import com.manabihub.course.dto.request.LessonBlockRequest;
import com.manabihub.course.dto.request.ReorderRequest;
import com.manabihub.course.dto.response.CourseBuilderResponse;

import java.util.UUID;

public interface CourseBuilderService {

    CourseBuilderResponse getBuilder(UUID draftId);

    CourseBuilderResponse createModule(UUID draftId, CourseModuleRequest request);

    CourseBuilderResponse updateModule(UUID draftId, UUID moduleId, CourseModuleRequest request);

    CourseBuilderResponse deleteModule(UUID draftId, UUID moduleId);

    CourseBuilderResponse reorderModules(UUID draftId, ReorderRequest request);

    CourseBuilderResponse createBlock(UUID draftId, UUID moduleId, LessonBlockRequest request);

    CourseBuilderResponse updateBlock(UUID draftId, UUID moduleId, UUID blockId, LessonBlockRequest request);

    CourseBuilderResponse deleteBlock(UUID draftId, UUID moduleId, UUID blockId);

    CourseBuilderResponse reorderBlocks(UUID draftId, UUID moduleId, ReorderRequest request);
}
