package com.manabihub.common.mail;

public interface EmailService {
    void sendEmail(String to, String subject, String body);

    void sendEmailSynchronously(String to, String subject, String body);
}
