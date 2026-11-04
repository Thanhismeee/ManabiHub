package com.manabihub.identity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class AvatarStaticResourceConfig implements WebMvcConfigurer {

    private final Path storageRoot;
    private final String publicPathPrefix;

    public AvatarStaticResourceConfig(
            @Value("${manabihub.user.avatar-storage-root:storage/user-avatars}") String storageRoot,
            @Value("${manabihub.user.avatar-public-path:/uploads/user-avatars}") String publicPathPrefix
    ) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.publicPathPrefix = publicPathPrefix.endsWith("/")
                ? publicPathPrefix.substring(0, publicPathPrefix.length() - 1)
                : publicPathPrefix;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create avatar storage directory", e);
        }

        registry.addResourceHandler(publicPathPrefix + "/**")
                .addResourceLocations("file:" + storageRoot.toString() + "/");
    }
}
