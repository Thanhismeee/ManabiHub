package com.manabihub.learning.config;

import com.manabihub.learning.interceptor.LearningSessionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class LearningWebMvcConfig implements WebMvcConfigurer {

    private final LearningSessionInterceptor learningSessionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(learningSessionInterceptor)
                .addPathPatterns("/api/v1/learning/**")
                .excludePathPatterns("/api/v1/learning-lease/**");
    }
}
