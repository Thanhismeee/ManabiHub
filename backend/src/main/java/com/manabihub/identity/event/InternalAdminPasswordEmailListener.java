package com.manabihub.identity.event;

import com.manabihub.common.mail.EmailService;
import com.manabihub.identity.service.InternalAdminCredentialDeliveryFailureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalAdminPasswordEmailListener {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
                    .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final EmailService emailService;
    private final InternalAdminCredentialDeliveryFailureService deliveryFailureService;

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void sendResetLink(InternalAdminPasswordResetIssuedEvent event) {
        String resetUrl = UriComponentsBuilder
                .fromUriString(frontendBaseUrl.replaceAll("/+$", ""))
                .path("/admin/reset-password")
                .fragment("token=" + event.rawToken())
                .build()
                .encode()
                .toUriString();
        String body = """
                <p>Xin chào %s,</p>
                <p>ManabiHub đã nhận được yêu cầu đặt lại mật khẩu cho tài khoản quản trị của bạn.</p>
                <p><a href="%s">Đặt lại mật khẩu quản trị</a></p>
                <p>Liên kết chỉ dùng được một lần và hết hạn lúc %s (giờ Việt Nam).
                Mật khẩu hiện tại vẫn có hiệu lực cho đến khi bạn hoàn tất bước này.</p>
                <p>Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email và liên hệ
                Quản trị hệ thống nếu nhận thấy hoạt động bất thường.</p>
                """.formatted(
                HtmlUtils.htmlEscape(event.fullName()),
                HtmlUtils.htmlEscape(resetUrl),
                HtmlUtils.htmlEscape(DATE_TIME_FORMAT.format(event.expiresAt()))
        );
        try {
            emailService.sendEmailSynchronously(
                    event.email(),
                    "Đặt lại mật khẩu Cổng quản trị ManabiHub",
                    body
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Internal administrator password reset email delivery failed",
                    exception
            );
            deliveryFailureService.revokePasswordReset(event.rawToken());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void sendPasswordChanged(InternalAdminPasswordChangedEvent event) {
        String body = """
                <p>Xin chào %s,</p>
                <p>Mật khẩu Cổng quản trị ManabiHub của bạn đã được thay đổi lúc
                %s (giờ Việt Nam).</p>
                <p>Tất cả phiên đăng nhập trước đó đã bị thu hồi. Nếu bạn không thực hiện
                thay đổi này, hãy liên hệ Quản trị hệ thống ngay lập tức.</p>
                """.formatted(
                HtmlUtils.htmlEscape(event.fullName()),
                HtmlUtils.htmlEscape(DATE_TIME_FORMAT.format(event.changedAt()))
        );
        try {
            emailService.sendEmailSynchronously(
                    event.email(),
                    "Mật khẩu quản trị ManabiHub đã được thay đổi",
                    body
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Internal administrator password changed email delivery failed",
                    exception
            );
            deliveryFailureService.recordPasswordChangedNotificationFailure(
                    event.adminAccountId()
            );
        }
    }
}
