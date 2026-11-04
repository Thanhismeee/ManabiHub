package com.manabihub.wallet.repository;

import com.manabihub.course.entity.Course;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.order.entity.Order;
import com.manabihub.order.entity.OrderItem;
import com.manabihub.order.enums.OrderStatus;
import com.manabihub.order.repository.OrderRepository;
import com.manabihub.order.repository.OrderItemRepository;
import com.manabihub.wallet.entity.EscrowLedger;
import com.manabihub.wallet.enums.EscrowStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.manabihub.kyc.domain.AppUser;
import com.manabihub.kyc.domain.TeacherKycStatus;
import com.manabihub.kyc.domain.TeacherProfile;
import com.manabihub.kyc.domain.UserStatus;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class EscrowLedgerRepositoryTest {

    private static PostgreSQLContainer<?> postgresContainer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine");
        postgresContainer.start();
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private EscrowLedgerRepository escrowLedgerRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void testGrossRevenueExcludesRefundedEscrow() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail("teacher@test.com");
        user.setFullName("Test Teacher");
        user.setUserStatus(UserStatus.ACTIVE);
        user = entityManager.merge(user);

        TeacherProfile profile = new TeacherProfile();
        profile.setId(UUID.randomUUID());
        profile.setUser(user);
        profile.setDisplayName("Test Teacher Display");
        profile.setKycStatus(TeacherKycStatus.APPROVED);
        profile = entityManager.merge(profile);

        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("Revenue Analytics Course");
        course.setSlug("revenue-analytics-course");
        course.setTeacher(profile);
        course.setPrice(new BigDecimal("1000"));
        course = courseRepository.save(course);

        Instant now = Instant.now();

        com.manabihub.identity.entity.AppUser studentUser = new com.manabihub.identity.entity.AppUser();
        studentUser.setId(UUID.randomUUID());
        studentUser.setEmail("student@test.com");
        studentUser.setFullName("Test Student");
        studentUser.setUserStatus(com.manabihub.identity.enums.AccountStatus.ACTIVE);
        studentUser = entityManager.merge(studentUser);

        com.manabihub.identity.entity.StudentProfile studentProfile = new com.manabihub.identity.entity.StudentProfile();
        studentProfile.setId(UUID.randomUUID());
        studentProfile.setUser(studentUser);
        studentProfile.setDisplayName("Student Display");
        studentProfile = entityManager.merge(studentProfile);

        // Order 1 (Held/PAID)
        Order o1 = new Order();
        o1.setId(UUID.randomUUID());
        o1.setStatus(OrderStatus.PAID);
        o1.setStudent(studentProfile);
        o1.setOrderCode("ORD-1");
        o1.setTotalAmount(new BigDecimal("1000"));
        o1 = orderRepository.save(o1);

        OrderItem oi1 = new OrderItem();
        oi1.setId(UUID.randomUUID());
        oi1.setOrder(o1);
        oi1.setCourse(course);
        oi1.setPrice(new BigDecimal("1000"));
        oi1 = orderItemRepository.save(oi1);

        EscrowLedger l1 = new EscrowLedger();
        l1.setId(UUID.randomUUID());
        l1.setCourse(course);
        l1.setTeacher(profile);
        l1.setOrder(o1);
        l1.setOrderItem(oi1);
        l1.setAmount(new BigDecimal("1000"));
        l1.setStatus(EscrowStatus.HELD);
        l1.setCreatedAt(now);
        escrowLedgerRepository.save(l1);

        // Order 2 (Refunded)
        Order o2 = new Order();
        o2.setId(UUID.randomUUID());
        o2.setStatus(OrderStatus.PAID);
        o2.setStudent(studentProfile);
        o2.setOrderCode("ORD-2");
        o2.setTotalAmount(new BigDecimal("1000"));
        o2 = orderRepository.save(o2);

        OrderItem oi2 = new OrderItem();
        oi2.setId(UUID.randomUUID());
        oi2.setOrder(o2);
        oi2.setCourse(course);
        oi2.setPrice(new BigDecimal("1000"));
        oi2 = orderItemRepository.save(oi2);

        EscrowLedger l2 = new EscrowLedger();
        l2.setId(UUID.randomUUID());
        l2.setCourse(course);
        l2.setTeacher(profile);
        l2.setOrder(o2);
        l2.setOrderItem(oi2);
        l2.setAmount(new BigDecimal("1000"));
        l2.setStatus(EscrowStatus.REFUNDED);
        l2.setCreatedAt(now);
        escrowLedgerRepository.save(l2);

        Instant start = now.minus(10, ChronoUnit.DAYS);
        Instant end = now.plus(1, ChronoUnit.DAYS);

        BigDecimal grossRevenue = escrowLedgerRepository.sumGrossRevenueByCourseIdAndDateRange(course.getId(), start, end);
        
        // Ensure gross revenue excludes REFUNDED ledgers
        assertEquals(0, new BigDecimal("1000").compareTo(grossRevenue));

        var revenue = escrowLedgerRepository.summarizeTeacherRevenueByCourse(profile.getId());
        assertEquals(1, revenue.size());
        var courseRevenue = revenue.get(0);
        assertEquals(2L, courseRevenue.getPurchaseCount());
        assertEquals(1L, courseRevenue.getRefundedCount());
        assertEquals(0, new BigDecimal("1000").compareTo(courseRevenue.getGrossRevenue()));
        assertEquals(0, new BigDecimal("1000").compareTo(courseRevenue.getTeacherNetRevenue()));
        assertEquals(0, new BigDecimal("1000").compareTo(courseRevenue.getHeldAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(courseRevenue.getReleasedAmount()));
        assertEquals(0, new BigDecimal("1000").compareTo(courseRevenue.getRefundedAmount()));
    }
}
