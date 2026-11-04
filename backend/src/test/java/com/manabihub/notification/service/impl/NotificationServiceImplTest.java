package com.manabihub.notification.service.impl;

import com.manabihub.common.mail.EmailService;
import com.manabihub.notification.entity.Notification;
import com.manabihub.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationServiceImpl service;

    @Test
    void createNotificationForAdminRole_ShouldAddressInternalAdminAccounts() {
        UUID firstAdminId = UUID.randomUUID();
        UUID secondAdminId = UUID.randomUUID();
        when(notificationRepository.findActiveAdminIdsByRoleCode("COURSE_MANAGER"))
                .thenReturn(List.of(firstAdminId, secondAdminId));
        when(notificationRepository.findActiveAdminEmailsByRoleCode("COURSE_MANAGER"))
                .thenReturn(List.of("manager@manabihub.local"));

        service.createNotificationForAdminRole(
                "COURSE_MANAGER",
                "Khóa học mới đang chờ xét duyệt",
                "Một khóa học đang chờ xét duyệt.",
                "COURSE_REVIEW",
                "/admin/courses/approvals/course-id"
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Notification>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(notificationRepository).saveAll(captor.capture());
        List<Notification> saved = ((List<Notification>) captor.getValue());

        assertEquals(List.of(firstAdminId, secondAdminId),
                saved.stream().map(Notification::getRecipientAdminId).toList());
        assertNull(saved.getFirst().getRecipientUserId());
        assertEquals("/admin/courses/approvals/course-id", saved.getFirst().getActionUrl());
        ArgumentCaptor<String> adminEmailBodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(
                eq("manager@manabihub.local"),
                eq("[ManabiHub] Khóa học mới đang chờ xét duyệt"),
                adminEmailBodyCaptor.capture()
        );
        String adminEmailBody = org.springframework.web.util.HtmlUtils
                .htmlUnescape(adminEmailBodyCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertTrue(
                adminEmailBody.contains("Một khóa học đang chờ xét duyệt."));
        org.junit.jupiter.api.Assertions.assertTrue(
                adminEmailBody.contains("Khóa học chờ duyệt"));
    }

    @Test
    void createNotification_ShouldKeepActionUrlAndEscapeEmailHtml() {
        UUID userId = UUID.randomUUID();

        service.createNotification(
                userId,
                "student@manabihub.local",
                "Khóa học <script>",
                "Nội dung <img src=x onerror=alert(1)>",
                "COURSE_APPROVAL",
                "/student/courses"
        );

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(notificationCaptor.capture());
        assertEquals("/student/courses", notificationCaptor.getValue().getActionUrl());

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendEmail(
                eq("student@manabihub.local"),
                eq("[ManabiHub] Khóa học <script>"),
                bodyCaptor.capture()
        );
        org.junit.jupiter.api.Assertions.assertFalse(bodyCaptor.getValue().contains("<script>"));
        org.junit.jupiter.api.Assertions.assertTrue(bodyCaptor.getValue().contains("&lt;script&gt;"));
        String decodedBody = org.springframework.web.util.HtmlUtils.htmlUnescape(bodyCaptor.getValue());
        org.junit.jupiter.api.Assertions.assertTrue(decodedBody.contains("Kết quả duyệt khóa học"));
        org.junit.jupiter.api.Assertions.assertFalse(bodyCaptor.getValue().contains("COURSE_APPROVAL"));
    }
}
