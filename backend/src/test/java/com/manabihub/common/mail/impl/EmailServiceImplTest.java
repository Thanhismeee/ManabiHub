package com.manabihub.common.mail.impl;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender javaMailSender;

    @InjectMocks
    private EmailServiceImpl emailService;

    @Test
    void skipsNonRoutableLocalAddressesBeforeCallingSmtp() {
        emailService.sendEmail("course.manager@manabihub.local", "Subject", "Body");
        emailService.sendEmailSynchronously("finance.manager@MANABIHUB.LOCAL ", "Subject", "Body");
        emailService.sendEmailSynchronously("demo@local", "Subject", "Body");

        verifyNoInteractions(javaMailSender);
    }

    @Test
    void continuesSendingToRoutableEmailAddresses() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendEmailSynchronously("manager@example.com", "Subject", "Body");

        verify(javaMailSender).send(mimeMessage);
    }
}
