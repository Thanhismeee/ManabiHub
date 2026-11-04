package com.manabihub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ManabiHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManabiHubApplication.class, args);
    }
}
