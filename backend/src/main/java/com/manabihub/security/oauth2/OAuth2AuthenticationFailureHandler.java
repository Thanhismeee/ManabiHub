package com.manabihub.security.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
// [CODE NOTE]: Trình xử lý sự kiện khi Đăng nhập Google (OAuth2) THẤT BẠI.
// Ví dụ: người dùng hủy cấp quyền, hoặc cấu hình sai. Nó sẽ đá người dùng về
// lại Frontend báo lỗi.
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final String OAUTH_FAILURE_CODE = "oauth2-login-failed";

    @Value("${app.frontend.login-url:http://localhost:5173/login}")
    private String frontendLoginUrl;

    @Override
    // [CODE NOTE]: Hàm onAuthenticationFailure() sẽ tạo URL điều hướng về trang
    // Login ở Frontend (cổng 5173).
    // Gắn thêm tham số ?error=... vào cuối URL.
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        log.warn("OAuth2 authentication failed: {}", exception.getClass().getSimpleName());

        // Redirect to frontend login page with error parameter
        String targetUrl = frontendLoginUrl + "?error=" + OAUTH_FAILURE_CODE;
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
