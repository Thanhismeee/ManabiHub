package com.manabihub.course.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.course.dto.response.CourseThumbnailUploadResponse;
import com.manabihub.course.service.CourseAssetStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teacher/courses/assets")
public class TeacherCourseAssetController {

    private final CourseAssetStorageService courseAssetStorageService;

    @PostMapping(value = "/thumbnails", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CourseThumbnailUploadResponse>> uploadThumbnail(
            @RequestPart("thumbnail") MultipartFile thumbnail
    ) {
        CourseThumbnailUploadResponse response = courseAssetStorageService.storeThumbnail(thumbnail);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                MessageCodes.MSG_COURSE_005,
                "Course thumbnail uploaded.",
                response
        ));
    }

}
