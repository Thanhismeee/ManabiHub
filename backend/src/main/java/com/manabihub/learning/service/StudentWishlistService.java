package com.manabihub.learning.service;

import com.manabihub.learning.dto.response.WishlistItemResponse;

import java.util.List;
import java.util.UUID;

public interface StudentWishlistService {

    List<WishlistItemResponse> getWishlist();

    WishlistItemResponse addCourse(UUID courseId);

    void removeCourse(UUID courseId);
}
