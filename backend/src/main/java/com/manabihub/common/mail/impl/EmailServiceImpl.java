package com.manabihub.common.mail.impl;

import com.manabihub.common.mail.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender javaMailSender;

    @Async
    @Override
    public void sendEmail(String to, String subject, String body) {
        try {
            sendEmailSynchronously(to, subject, body);
        } catch (RuntimeException exception) {
            log.error("Asynchronous email delivery failed", exception);
        }
    }

    @Override
    public void sendEmailSynchronously(String to, String subject, String body) {
        if (isNonRoutableLocalAddress(to)) {
            log.info("Skipped email delivery to a non-routable .local address");
            return;
        }
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);

            javaMailSender.send(message);
            log.info("Email sent successfully");
        } catch (MessagingException exception) {
            throw new IllegalStateException("Email delivery failed", exception);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Email delivery failed", exception);
        }
    }

    private boolean isNonRoutableLocalAddress(String recipient) {
        if (recipient == null) {
            return false;
        }
        int separatorIndex = recipient.lastIndexOf('@');
        if (separatorIndex < 0 || separatorIndex == recipient.length() - 1) {
            return false;
        }
        String domain = recipient.substring(separatorIndex + 1).trim().toLowerCase(Locale.ROOT);
        return domain.equals("local") || domain.endsWith(".local");
    }
}
