package com.manabihub.identity.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.repository.PublicUserDeviceRepository;
import com.manabihub.identity.service.PublicUserSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PublicUserSessionServiceImplConcurrencyTest {

    @Autowired
    private PublicUserSessionService publicUserSessionService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PublicUserDeviceRepository deviceRepository;

    private UUID testUserId;

    @BeforeEach
    void setUp() {
        AppUser user = AppUser.builder()
                .id(UUID.randomUUID())
                .email("test.concurrency." + UUID.randomUUID() + "@manabihub.local")
                .fullName("Test Concurrency")
                .build();
        user = appUserRepository.saveAndFlush(user);
        testUserId = user.getId();
        
        // Verify user exists in main thread
        assertTrue(appUserRepository.findById(testUserId).isPresent(), "User should be saved");
    }

    @AfterEach
    void tearDown() {
        appUserRepository.deleteById(testUserId);
    }

    @Test
    void testConcurrentDeviceLimit() throws InterruptedException {
        // Pre-register 1 device
        publicUserSessionService.createSession(testUserId, "device-key-1", "User Agent 1", "Device 1");

        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    latch.await(); // Wait for all threads to be ready
                    System.out.println("Thread checking user: " + appUserRepository.findById(testUserId).isPresent());
                    publicUserSessionService.createSession(testUserId, "device-key-concurrent-" + index, "User Agent " + index, "Device " + index);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (MessageCodes.PUBLIC_DEVICE_LIMIT_REACHED.equals(e.getMessageCode())) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        latch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);

        long activeDevices = deviceRepository.countActiveDevicesByUserId(testUserId);
        
        // Max devices is 2. We pre-registered 1. Only 1 concurrent thread should succeed.
        assertTrue(activeDevices <= 2, "Active devices should not exceed the limit of 2, but was: " + activeDevices);
        assertEquals(1, successCount.get(), "Only 1 concurrent login should succeed");
        assertEquals(4, failCount.get(), "4 concurrent logins should be rejected");

        executor.shutdown();
    }
}
