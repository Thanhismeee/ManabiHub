package com.manabihub.payout.repository;

import com.manabihub.payout.dto.request.PayoutQueueFilterRequest;
import com.manabihub.payout.enums.WithdrawalStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("it")
class PayoutQueueSpecificationPostgresTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("manabihub_payout_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findsPendingRequestWhenOptionalFiltersAreAbsent() {
        UUID userId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO app_users (id, email, full_name, created_at, updated_at)
                VALUES (?, 'payout-teacher@test.com', 'Payout Teacher', now(), now())
                """, userId);
        jdbcTemplate.update("""
                INSERT INTO teacher_profiles (id, user_id, display_name, created_at, updated_at)
                VALUES (?, ?, 'Payout Teacher', now(), now())
                """, teacherId, userId);
        jdbcTemplate.update("""
                INSERT INTO wallets (
                    id, owner_type, teacher_id, balance, frozen_balance, currency
                )
                VALUES (?, 'TEACHER', ?, 0, 0, 'VND')
                """, walletId, teacherId);
        jdbcTemplate.update("""
                INSERT INTO withdrawal_requests (
                    id, owner_type, teacher_id, wallet_id, amount, status,
                    bank_account_snapshot, requested_at, created_at, updated_at
                )
                VALUES (?, 'TEACHER', ?, ?, 200000, 'PENDING', '{}'::jsonb, now(), now(), now())
                """, withdrawalId, teacherId, walletId);

        PayoutQueueFilterRequest filter = new PayoutQueueFilterRequest();
        filter.setStatus(WithdrawalStatus.PENDING);

        var result = withdrawalRequestRepository.findAll(
                PayoutQueueSpecification.from(filter),
                PageRequest.of(0, 10)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getId()).isEqualTo(withdrawalId);
    }
}
