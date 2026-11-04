package com.manabihub.course.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.course.dto.request.CourseModuleRequest;
import com.manabihub.course.dto.request.LessonBlockRequest;
import com.manabihub.course.dto.request.ReorderRequest;
import com.manabihub.course.dto.response.CourseBuilderResponse;
import com.manabihub.course.service.CourseBuilderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teacher/courses/drafts/{draftId}/builder")
public class TeacherCourseBuilderController {

    private final CourseBuilderService courseBuilderService;

    @GetMapping
    public ResponseEntity<ApiResponse<CourseBuilderResponse>> getBuilder(@PathVariable UUID draftId) {
        CourseBuilderResponse response = courseBuilderService.getBuilder(draftId);

        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.CONTENT_UPDATED,
                "Course builder loaded.",
                response
        ));
    }

    @PostMapping("/modules")
    public ResponseEntity<ApiResponse<CourseBuilderResponse>> createModule(
            @PathVariable UUID draftId,
            @Valid @RequestBody CourseModuleRequest request
    ) {
        CourseBuilderResponse response = courseBuilderService.createModule(draftId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                MessageCodes.CONTENT_CREATED,
                "Course module created.",
                response
        ));
    }

    @PutMapping("/modules/{moduleId}")
    public ResponseEntity<ApiResponse<CourseBuilderResponse>> updateModule(
            @PathVariable UUID draftId,
            @PathVariable UUID moduleId,
            @Valid @RequestBody CourseModuleRequest request
    ) {
        CourseBuilderResponse response = courseBuilderService.updateModule(draftId, moduleId, request);

        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.CONTENT_UPDATED,
                "Course module updated.",
                response
        ));
    }

    @DeleteMapping("/modules/{moduleId}")
    public ResponseEntity<ApiResponse<CourseBuilderResponse>> deleteModule(
            @PathVariable UUID draftId,
            @PathVariable UUID moduleId
    ) {
        CourseBuilderResponse response = courseBuilderService.deleteModule(draftId, moduleId);

        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.CONTENT_DELETED,
                "Course module deleted.",
                response
        ));
    }

    @PutMapping("/modules/order")
    public ResponseEntity<ApiResponse<CourseBuilderResponse>> reorderModules(
            @PathVariable UUID draftId,
            @RequestBody ReorderRequest request
    ) {
        CourseBuilderResponse response = courseBuilderService.reorderModules(draftId, request);

        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.CONTENT_UPDATED,
                "Course modules reordered.",
                response
        ));
    }

    @PostMapping("/modules/{moduleId}/blocks")
    public ResponseEntity<ApiResponse<CourseBuilderResponse>> createBlock(
            @PathVariable UUID draftId,
            @PathVariable UUID moduleId,
            @Valid @RequestBody LessonBlockRequest request
    ) {
        CourseBuilderResponse response = courseBuilderService.createBlock(draftId, moduleId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                MessageCodes.CONTENT_CREATED,
                "Course content block created.",
                response
        ));
    }

    @PutMapping("/modules/{moduleId}/blocks/{blockId}")
    public ResponseEntity<ApiResponse<CourseBuilderResponse>> updateBlock(
            @PathVariable UUID draftId,
            @PathVariable UUID moduleId,
            @PathVariable UUID blockId,
            @Valid @RequestBody LessonBlockRequest request
    ) {
        CourseBuilderResponse response = courseBuilderService.updateBlock(draftId, moduleId, blockId, request);

        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.CONTENT_UPDATED,
                "Course content block updated.",
                response
        ));
    }

    @DeleteMapping("/modules/{moduleId}/blocks/{blockId}")
    public ResponseEntity<ApiResponse<CourseBuilderResponse>> deleteBlock(
            @PathVariable UUID draftId,
            @PathVariable UUID moduleId,
            @PathVariable UUID blockId
    ) {
        CourseBuilderResponse response = courseBuilderService.deleteBlock(draftId, moduleId, blockId);

        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.CONTENT_DELETED,
                "Course content block deleted.",
                response
        ));
    }

    @PutMapping("/modules/{moduleId}/blocks/order")
    public ResponseEntity<ApiResponse<CourseBuilderResponse>> reorderBlocks(
            @PathVariable UUID draftId,
            @PathVariable UUID moduleId,
            @RequestBody ReorderRequest request
    ) {
        CourseBuilderResponse response = courseBuilderService.reorderBlocks(draftId, moduleId, request);

        return ResponseEntity.ok(ApiResponse.success(
                MessageCodes.CONTENT_UPDATED,
                "Course content blocks reordered.",
                response
        ));
    }
}
