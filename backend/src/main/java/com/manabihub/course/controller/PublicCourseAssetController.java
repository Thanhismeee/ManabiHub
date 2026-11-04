package com.manabihub.course.controller;

import com.manabihub.course.service.CourseAssetStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/uploads/course-thumbnails")
public class PublicCourseAssetController {

    private final CourseAssetStorageService courseAssetStorageService;

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<byte[]> getThumbnail(@PathVariable String fileName) {
        return courseAssetStorageService.loadThumbnail(fileName)
                .map(thumbnail -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(thumbnail.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(thumbnail.content()))
                .orElseGet(() -> ResponseEntity.<byte[]>notFound().build());
    }
}
