package com.manabihub.identity.controller;

import com.manabihub.common.response.ApiResponse;
import com.manabihub.identity.service.UserAvatarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/avatar")
@RequiredArgsConstructor
public class UserAvatarController {

    private final UserAvatarService userAvatarService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ResponseEntity<ApiResponse<String>> uploadAvatar(@RequestPart("file") MultipartFile file) {
        String url = userAvatarService.uploadAvatar(file);
        return ResponseEntity.ok(ApiResponse.success(url));
    }
}
