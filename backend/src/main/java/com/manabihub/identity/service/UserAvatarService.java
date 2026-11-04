package com.manabihub.identity.service;

import org.springframework.web.multipart.MultipartFile;

public interface UserAvatarService {
    String uploadAvatar(MultipartFile file);
}
