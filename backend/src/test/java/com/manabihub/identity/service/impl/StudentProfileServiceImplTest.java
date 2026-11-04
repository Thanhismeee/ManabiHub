package com.manabihub.identity.service.impl;

import com.manabihub.identity.dto.request.UpdateStudentProfileRequest;
import com.manabihub.identity.dto.response.StudentProfileResponse;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.mapper.StudentProfileMapper;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentProfileServiceImplTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @Mock
    private CurrentUserService currentUserService;

    private StudentProfileServiceImpl studentProfileService;

    @BeforeEach
    void setUp() {
        studentProfileService = new StudentProfileServiceImpl(
                appUserRepository,
                studentProfileRepository,
                new StudentProfileMapper(),
                currentUserService
        );
    }

    @Test
    void updateMyProfileCreatesProfileDuringFirstOnboarding() {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.builder()
                .id(userId)
                .email("student@manabihub.local")
                .fullName("Google Name")
                .avatarUrl("https://example.com/avatar.png")
                .build();
        UpdateStudentProfileRequest request = request("Nguyen Van A", "0912345678", "N3");

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentProfileResponse response = studentProfileService.updateMyProfile(request);

        ArgumentCaptor<StudentProfile> profileCaptor = ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentProfileRepository).save(profileCaptor.capture());
        StudentProfile createdProfile = profileCaptor.getValue();

        assertSame(user, createdProfile.getUser());
        assertSame(createdProfile, user.getStudentProfile());
        assertEquals("Nguyen Van A", response.getFullName());
        assertEquals("0912345678", response.getPhoneNumber());
        assertEquals("Nguyen Van A", response.getDisplayName());
        assertEquals("N3", response.getJlptGoal());
        assertEquals("https://example.com/avatar.png", response.getAvatarUrl());
    }

    @Test
    void updateMyProfileKeepsAndUpdatesExistingProfile() {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.builder()
                .id(userId)
                .email("student@manabihub.local")
                .fullName("Old Name")
                .avatarUrl("/uploads/user-avatars/old.png")
                .build();
        StudentProfile existingProfile = StudentProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .displayName("Old Display Name")
                .jlptGoal("N5")
                .build();
        UpdateStudentProfileRequest request = request("Tran Thi B", null, "N2");

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(existingProfile));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentProfileResponse response = studentProfileService.updateMyProfile(request);

        verify(studentProfileRepository).save(existingProfile);
        assertSame(existingProfile, user.getStudentProfile());
        assertEquals("Tran Thi B", response.getFullName());
        assertEquals("Tran Thi B", response.getDisplayName());
        assertEquals("N2", response.getJlptGoal());
        assertEquals("/uploads/user-avatars/old.png", response.getAvatarUrl());
    }

    @Test
    void updateMyProfileNormalizesInternationalPhoneNumber() {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.builder()
                .id(userId)
                .email("student@manabihub.local")
                .fullName("Google Name")
                .build();
        StudentProfile existingProfile = StudentProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .displayName("Old Display Name")
                .jlptGoal("N5")
                .build();
        UpdateStudentProfileRequest request = request("Nguyen Van A", "+84912345678", "N3");

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(existingProfile));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentProfileResponse response = studentProfileService.updateMyProfile(request);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());

        assertEquals("0912345678", userCaptor.getValue().getPhoneNumber());
        assertEquals("0912345678", response.getPhoneNumber());
    }

    @Test
    void updateMyProfileClearsPhoneNumberWhenProfileScreenSendsEmptyValue() {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.builder()
                .id(userId)
                .email("student@manabihub.local")
                .fullName("Google Name")
                .phoneNumber("0912345678")
                .build();
        StudentProfile existingProfile = StudentProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .displayName("Old Display Name")
                .jlptGoal("N5")
                .build();
        UpdateStudentProfileRequest request = request("Nguyen Van A", "", "N3");

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(existingProfile));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentProfileResponse response = studentProfileService.updateMyProfile(request);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());

        assertNull(userCaptor.getValue().getPhoneNumber());
        assertNull(response.getPhoneNumber());
    }

    @Test
    void verifiedPhoneNumberCannotBeChangedFromProfileUpdate() {
        UUID userId = UUID.randomUUID();
        AppUser user = AppUser.builder()
                .id(userId)
                .email("student@manabihub.local")
                .fullName("Google Name")
                .phoneNumber("0912345678")
                .phoneVerifiedAt(java.time.Instant.now())
                .build();
        StudentProfile profile = StudentProfile.builder().id(UUID.randomUUID()).user(user).build();
        UpdateStudentProfileRequest request = request("Nguyen Van A", "0987654321", "N3");

        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));

        var exception = assertThrows(com.manabihub.common.exception.BusinessException.class,
                () -> studentProfileService.updateMyProfile(request));

        assertEquals("PHONE_VERIFICATION_ALREADY_VERIFIED", exception.getMessageCode());
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    private UpdateStudentProfileRequest request(String name, String phoneNumber, String jlptGoal) {
        UpdateStudentProfileRequest request = new UpdateStudentProfileRequest();
        request.setFullName(name);
        request.setPhoneNumber(phoneNumber);
        request.setDisplayName(name);
        request.setJlptGoal(jlptGoal);
        return request;
    }
}
