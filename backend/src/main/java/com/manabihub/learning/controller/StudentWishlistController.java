package com.manabihub.learning.controller;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.response.ApiResponse;
import com.manabihub.learning.dto.response.WishlistItemResponse;
import com.manabihub.learning.service.StudentWishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/wishlist")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentWishlistController {

    private final StudentWishlistService wishlistService;

    @GetMapping
    public ApiResponse<List<WishlistItemResponse>> getWishlist() {
        return ApiResponse.success(
                MessageCodes.COMMON_SUCCESS,
                "Wishlist loaded.",
                wishlistService.getWishlist()
        );
    }

    @PostMapping("/{courseId}")
    public ApiResponse<WishlistItemResponse> addCourse(@PathVariable UUID courseId) {
        return ApiResponse.success(
                MessageCodes.LEARNING_WISHLIST_ADDED,
                "Course added to wishlist.",
                wishlistService.addCourse(courseId)
        );
    }

    @DeleteMapping("/{courseId}")
    public ApiResponse<Void> removeCourse(@PathVariable UUID courseId) {
        wishlistService.removeCourse(courseId);
        return ApiResponse.success(
                MessageCodes.LEARNING_WISHLIST_REMOVED,
                "Course removed from wishlist.",
                null
        );
    }
}
