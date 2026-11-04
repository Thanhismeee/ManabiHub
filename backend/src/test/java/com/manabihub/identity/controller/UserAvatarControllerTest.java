package com.manabihub.identity.controller;

import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.manabihub.identity.service.CurrentUserService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAvatarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private AuditLogService auditLogService;

    private AppUser studentUser;
    private AppUser adminUser;
    @TempDir
    static Path tempAvatarDir;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("manabihub.user.avatar-storage-root", () -> tempAvatarDir.toAbsolutePath().toString());
    }

    private static final byte[] VALID_PNG = new byte[]{ (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x00 };
    private static final byte[] VALID_JPEG = new byte[]{ (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };

    @BeforeEach
    void setUp() throws Exception {
        studentUser = AppUser.builder()
                .email("student_avatar_test_" + UUID.randomUUID() + "@example.com")
                .fullName("Student Name")
                .build();
        studentUser = appUserRepository.save(studentUser);

        adminUser = AppUser.builder()
                .email("admin_avatar_test_" + UUID.randomUUID() + "@example.com")
                .fullName("Admin Name")
                .build();
        adminUser = appUserRepository.save(adminUser);
    }

    @AfterEach
    void tearDown() {
        if (studentUser != null) appUserRepository.delete(studentUser);
        if (adminUser != null) appUserRepository.delete(adminUser);
    }

    @Test
    @DisplayName("Upload avatar - Success for STUDENT")
    @WithMockUser(roles = {"STUDENT"})
    void uploadAvatar_Success_Student() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(studentUser.getId());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                VALID_PNG
        );

        mockMvc.perform(multipart("/api/v1/users/avatar")
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.startsWith("/uploads/user-avatars/")));

        // Verify DB update
        AppUser updatedUser = appUserRepository.findById(studentUser.getId()).orElseThrow();
        assertNotNull(updatedUser.getAvatarUrl());
        assertTrue(updatedUser.getAvatarUrl().startsWith("/uploads/user-avatars/"));
    }

    @Test
    @DisplayName("Upload avatar - Success for TEACHER")
    @WithMockUser(roles = {"TEACHER"})
    void uploadAvatar_Success_Teacher() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(studentUser.getId());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                VALID_JPEG
        );

        mockMvc.perform(multipart("/api/v1/users/avatar")
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.startsWith("/uploads/user-avatars/")));
    }

    @Test
    @DisplayName("Upload avatar - Unauthenticated returns 401")
    void uploadAvatar_Unauthenticated_401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", VALID_PNG);

        mockMvc.perform(multipart("/api/v1/users/avatar")
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Upload avatar - ADMIN returns 403")
    @WithMockUser(roles = {"ADMIN"})
    void uploadAvatar_Admin_403() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(adminUser.getId());

        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", VALID_PNG);

        mockMvc.perform(multipart("/api/v1/users/avatar")
                        .file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Upload avatar - Oversize file returns 400")
    @WithMockUser(roles = {"STUDENT"})
    void uploadAvatar_Oversize_400() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(studentUser.getId());

        byte[] bigFile = new byte[3 * 1024 * 1024]; // 3MB
        System.arraycopy(VALID_PNG, 0, bigFile, 0, VALID_PNG.length);

        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", bigFile);

        mockMvc.perform(multipart("/api/v1/users/avatar")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageCode").value(MessageCodes.COMMON_BAD_REQUEST));
    }

    @Test
    @DisplayName("Upload avatar - MIME spoofing returns 400")
    @WithMockUser(roles = {"STUDENT"})
    void uploadAvatar_Spoofing_400() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(studentUser.getId());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png", // valid content type, fake extension, but invalid bytes
                "this is actually a text file with no magic bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/users/avatar")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageCode").value(MessageCodes.COMMON_BAD_REQUEST));
    }

    @Test
    @DisplayName("Upload avatar - Invalid MIME type returns 400")
    @WithMockUser(roles = {"STUDENT"})
    void uploadAvatar_InvalidMimeType_400() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(studentUser.getId());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                VALID_PNG // valid magic bytes, but invalid content type
        );

        mockMvc.perform(multipart("/api/v1/users/avatar")
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageCode").value(MessageCodes.COMMON_BAD_REQUEST));
    }

    @Test
    @DisplayName("Upload avatar - Lifecycle: Public access works after successful upload")
    @WithMockUser(roles = {"STUDENT"})
    void uploadAvatar_Lifecycle_PublicAccess() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(studentUser.getId());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                VALID_PNG
        );

        String jsonResponse = mockMvc.perform(multipart("/api/v1/users/avatar")
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String avatarUrl = com.jayway.jsonpath.JsonPath.read(jsonResponse, "$.data");

        // Use standard WebMvc request without auth to test permitAll and resource handler
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(avatarUrl))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(MediaType.IMAGE_PNG_VALUE));
    }

    @Test
    @DisplayName("Upload avatar - Rollback deletes new file and keeps database value")
    @WithMockUser(roles = {"STUDENT"})
    void uploadAvatar_Rollback_DeletesNewFile() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(studentUser.getId());
        Set<String> filesBeforeUpload = storedFileNames();
        doThrow(new RuntimeException("forced audit failure"))
                .when(auditLogService)
                .logUserAction(any(), any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(multipart("/api/v1/users/avatar")
                        .file(validPngFile()))
                .andExpect(status().isInternalServerError());

        assertEquals(filesBeforeUpload, storedFileNames());
        assertNull(appUserRepository.findById(studentUser.getId()).orElseThrow().getAvatarUrl());
    }

    @Test
    @DisplayName("Upload avatar - Commit deletes previous local avatar")
    @WithMockUser(roles = {"STUDENT"})
    void uploadAvatar_Commit_DeletesPreviousLocalAvatar() throws Exception {
        Path oldAvatar = tempAvatarDir.resolve("old-local-avatar.png");
        Files.write(oldAvatar, VALID_PNG);
        studentUser.setAvatarUrl("/uploads/user-avatars/old-local-avatar.png");
        studentUser = appUserRepository.saveAndFlush(studentUser);
        when(currentUserService.getCurrentUserId()).thenReturn(studentUser.getId());

        mockMvc.perform(multipart("/api/v1/users/avatar")
                        .file(validPngFile()))
                .andExpect(status().isOk());

        assertFalse(Files.exists(oldAvatar));
    }

    @Test
    @DisplayName("Upload avatar - Commit preserves external avatar source")
    @WithMockUser(roles = {"STUDENT"})
    void uploadAvatar_Commit_PreservesExternalAvatarSource() throws Exception {
        Path similarlyPrefixedFile = tempAvatarDir.resolve("backup").resolve("external-avatar.png");
        Files.createDirectories(similarlyPrefixedFile.getParent());
        Files.write(similarlyPrefixedFile, VALID_PNG);
        studentUser.setAvatarUrl("/uploads/user-avatars-backup/external-avatar.png");
        studentUser = appUserRepository.saveAndFlush(studentUser);
        when(currentUserService.getCurrentUserId()).thenReturn(studentUser.getId());

        mockMvc.perform(multipart("/api/v1/users/avatar")
                        .file(validPngFile()))
                .andExpect(status().isOk());

        assertTrue(Files.exists(similarlyPrefixedFile));
    }

    private Set<String> storedFileNames() throws IOException {
        try (var paths = Files.list(tempAvatarDir)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }
    }

    private MockMultipartFile validPngFile() {
        return new MockMultipartFile("file", "avatar.png", "image/png", VALID_PNG);
    }
}
