package com.manabihub.order.service.impl;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.response.PageResponse;
import com.manabihub.course.entity.Course;
import com.manabihub.course.enums.CourseStatus;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.identity.service.CurrentUserService;
import com.manabihub.learning.entity.Enrollment;
import com.manabihub.learning.enums.EnrollmentStatus;
import com.manabihub.learning.repository.EnrollmentRepository;
import com.manabihub.learning.service.EnrollmentProgressResetService;
import com.manabihub.order.entity.Order;
import com.manabihub.order.entity.OrderItem;
import com.manabihub.order.dto.response.OrderResponse;
import com.manabihub.order.enums.OrderStatus;
import com.manabihub.order.enums.OrderType;
import com.manabihub.order.mapper.OrderMapper;
import com.manabihub.order.repository.OrderItemRepository;
import com.manabihub.order.repository.OrderRepository;
import com.manabihub.wallet.config.WalletPaymentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderServiceImpl}.
 * <p>
 * Grouped with {@code @Nested} so Surefire reports one summary line per Report 5.1 sheet:
 * <pre>
 *   OrderServiceImplTest$CreateOrder                 -> sheet 36 createOrder
 *   OrderServiceImplTest$CreateTopUpOrder            -> sheet 37 createTopUpOrder
 *   OrderServiceImplTest$GetOrderForCurrentStudent   -> sheet 38 getOrderForCurrentStudent
 *   OrderServiceImplTest$GetOrdersForCurrentStudent  -> sheet 39 getOrdersForCurrentStudent
 * </pre>
 * {@code org.junit.jupiter.api.Order} is written out in full because this file also uses the
 * {@code com.manabihub.order.entity.Order} entity.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private EnrollmentProgressResetService enrollmentProgressResetService;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private OrderMapper orderMapper;
    @Mock private WalletPaymentProperties walletPaymentProperties;

    @InjectMocks
    private OrderServiceImpl service;

    private UUID userId;
    private StudentProfile student;
    private Course course;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        student = StudentProfile.builder().id(UUID.randomUUID()).build();
        course = Course.builder()
                .id(UUID.randomUUID())
                .title("N3 Grammar")
                .price(new BigDecimal("150000.00"))
                .currency("VND")
                .status(CourseStatus.PUBLISHED)
                .build();

        lenient().when(currentUserService.getCurrentUserId()).thenReturn(userId);
        lenient().when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.of(student));
        lenient().when(walletPaymentProperties.getTopUpMinAmount())
                .thenReturn(new BigDecimal("10000"));
        lenient().when(walletPaymentProperties.getTopUpMaxAmount())
                .thenReturn(new BigDecimal("100000000"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 36 — createOrder (UC-08 Purchase Course)
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 36 - createOrder (UC-08)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CreateOrder {

        @Test
        @org.junit.jupiter.api.Order(1)
        @DisplayName("UTCID01 (N) - published course, not owned yet -> PENDING order + item")
        void createOrder_publishedCourse_createsPendingOrderWithItem() {
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            when(enrollmentRepository.findByStudent_IdAndCourse_Id(student.getId(), course.getId()))
                    .thenReturn(Optional.empty());
            when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order created = service.createOrder(course.getId());

            assertEquals(OrderStatus.PENDING, created.getStatus());
            assertEquals(new BigDecimal("150000.00"), created.getTotalAmount());
            assertEquals("VND", created.getCurrency());

            ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
            verify(orderItemRepository).save(itemCaptor.capture());
            assertEquals(course.getId(), itemCaptor.getValue().getCourse().getId());
            assertEquals(new BigDecimal("150000.00"), itemCaptor.getValue().getPrice());
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        @DisplayName("UTCID02 (N) - REFUNDED enrollment -> repurchase allowed")
        void createOrder_refundedEnrollment_allowsRepurchase() {
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            Enrollment refunded = Enrollment.builder().status(EnrollmentStatus.REFUNDED).build();
            when(enrollmentRepository.findByStudent_IdAndCourse_Id(student.getId(), course.getId()))
                    .thenReturn(Optional.of(refunded));
            when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order created = service.createOrder(course.getId());

            assertEquals(OrderStatus.PENDING, created.getStatus());
            verify(orderItemRepository).save(any(OrderItem.class));
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        @DisplayName("UTCID03 (A) - course not found -> COMMON_NOT_FOUND")
        void createOrder_courseNotFound_throws() {
            when(courseRepository.findById(course.getId())).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> service.createOrder(course.getId()));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(4)
        @DisplayName("UTCID04 (A) - DRAFT course -> ORDER_COURSE_NOT_PUBLISHED")
        void createOrder_courseNotPublished_throws() {
            course.setStatus(CourseStatus.DRAFT);
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

            assertThrows(BusinessException.class, () -> service.createOrder(course.getId()));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(5)
        @DisplayName("UTCID05 (A) - ACTIVE enrollment -> ORDER_ALREADY_ENROLLED")
        void createOrder_alreadyEnrolled_throws() {
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            Enrollment active = Enrollment.builder().status(EnrollmentStatus.ACTIVE).build();
            when(enrollmentRepository.findByStudent_IdAndCourse_Id(student.getId(), course.getId()))
                    .thenReturn(Optional.of(active));

            assertThrows(BusinessException.class, () -> service.createOrder(course.getId()));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(6)
        @DisplayName("UTCID06 (A) - REVOKED enrollment -> ORDER_ALREADY_ENROLLED")
        void createOrder_revokedEnrollment_doesNotBypassModeration() {
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            Enrollment revoked = Enrollment.builder().status(EnrollmentStatus.REVOKED).build();
            when(enrollmentRepository.findByStudent_IdAndCourse_Id(student.getId(), course.getId()))
                    .thenReturn(Optional.of(revoked));

            assertThrows(BusinessException.class, () -> service.createOrder(course.getId()));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(7)
        @DisplayName("UTCID07 (A) - no student profile -> LEARNING_STUDENT_PROFILE_NOT_FOUND")
        void createOrder_studentProfileMissing_throws() {
            when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> service.createOrder(course.getId()));
            verify(courseRepository, never()).findById(any());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(8)
        @DisplayName("UTCID08 (B) - free course, price 0 -> PENDING order with total 0")
        void createOrder_zeroPriceCourse_createsZeroAmountOrder() {
            course.setPrice(BigDecimal.ZERO);
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            when(enrollmentRepository.findByStudent_IdAndCourse_Id(student.getId(), course.getId()))
                    .thenReturn(Optional.empty());
            when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order created = service.createOrder(course.getId());

            assertEquals(0, created.getTotalAmount().compareTo(BigDecimal.ZERO));
            assertEquals(OrderStatus.PENDING, created.getStatus());
            verify(orderItemRepository).save(any(OrderItem.class));
        }

        @Test
        @org.junit.jupiter.api.Order(9)
        @DisplayName("Expired fixed course deadline -> ORDER_COURSE_ACCESS_ENDED")
        void createOrder_fixedCourseDeadlineEnded_rejectsCheckout() {
            course.setAccessExpiresAt(Instant.now().minusSeconds(60));
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.createOrder(course.getId())
            );

            assertEquals(MessageCodes.ORDER_COURSE_ACCESS_ENDED, error.getMessageCode());
            verify(enrollmentRepository, never())
                    .findByStudent_IdAndCourse_Id(any(), any());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(10)
        @DisplayName("Elapsed ACTIVE enrollment -> repurchase allowed")
        void createOrder_elapsedActiveEnrollment_allowsRepurchase() {
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            Enrollment elapsed = Enrollment.builder()
                    .status(EnrollmentStatus.ACTIVE)
                    .expiresAt(Instant.now().minusSeconds(60))
                    .build();
            when(enrollmentRepository.findByStudent_IdAndCourse_Id(student.getId(), course.getId()))
                    .thenReturn(Optional.of(elapsed));
            when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order created = service.createOrder(course.getId());

            assertEquals(OrderStatus.PENDING, created.getStatus());
            verify(orderItemRepository).save(any(OrderItem.class));
        }

        @Test
        @org.junit.jupiter.api.Order(11)
        @DisplayName("REFUND_PENDING enrollment -> ORDER_ALREADY_ENROLLED")
        void createOrder_refundPendingEnrollment_rejectsRepurchase() {
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
            Enrollment refundPending = Enrollment.builder()
                    .status(EnrollmentStatus.REFUND_PENDING)
                    .expiresAt(Instant.now().minusSeconds(60))
                    .build();
            when(enrollmentRepository.findByStudent_IdAndCourse_Id(student.getId(), course.getId()))
                    .thenReturn(Optional.of(refundPending));

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> service.createOrder(course.getId())
            );

            assertEquals(MessageCodes.ORDER_ALREADY_ENROLLED, error.getMessageCode());
            verify(orderRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 37 — createTopUpOrder (UC-08 Purchase Course) — 7 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 37 - createTopUpOrder (UC-08)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CreateTopUpOrder {

        @Test
        @org.junit.jupiter.api.Order(1)
        @DisplayName("UTCID01 (N) - amount 50000 -> PENDING WALLET_TOPUP order, no item")
        void createTopUpOrder_normalAmount_createsPendingTopUpOrderWithoutItem() {
            when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order created = service.createTopUpOrder(new BigDecimal("50000"));

            assertEquals(OrderType.WALLET_TOPUP, created.getType());
            assertEquals(OrderStatus.PENDING, created.getStatus());
            assertEquals("VND", created.getCurrency());
            assertEquals(new BigDecimal("50000"), created.getTotalAmount());
            verify(orderItemRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        @DisplayName("UTCID02 (B) - amount 10000 = configured minimum -> accepted")
        void createTopUpOrder_acceptsConfiguredMinimum() {
            when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order created = service.createTopUpOrder(new BigDecimal("10000"));

            assertEquals(new BigDecimal("10000"), created.getTotalAmount());
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        @DisplayName("UTCID03 (B) - amount 100000000 = configured maximum -> accepted")
        void createTopUpOrder_acceptsConfiguredMaximum() {
            when(orderRepository.existsByOrderCode(anyString())).thenReturn(false);
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order created = service.createTopUpOrder(new BigDecimal("100000000"));

            assertEquals(new BigDecimal("100000000"), created.getTotalAmount());
        }

        @Test
        @org.junit.jupiter.api.Order(4)
        @DisplayName("UTCID04 (B) - amount 9999 = minimum - 1 -> COMMON_BAD_REQUEST")
        void createTopUpOrder_rejectsAmountBelowConfiguredMinimum() {
            assertThrows(BusinessException.class,
                    () -> service.createTopUpOrder(new BigDecimal("9999")));

            verify(orderRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(5)
        @DisplayName("UTCID05 (B) - amount 100000001 = maximum + 1 -> COMMON_BAD_REQUEST")
        void createTopUpOrder_rejectsAmountAboveConfiguredMaximum() {
            assertThrows(BusinessException.class,
                    () -> service.createTopUpOrder(new BigDecimal("100000001")));

            verify(orderRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(6)
        @DisplayName("UTCID06 (A) - amount null -> COMMON_BAD_REQUEST")
        void createTopUpOrder_rejectsNullAmount() {
            assertThrows(BusinessException.class, () -> service.createTopUpOrder(null));

            verify(orderRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.Order(7)
        @DisplayName("UTCID07 (A) - amount 10000.50 not a whole VND -> COMMON_BAD_REQUEST")
        void createTopUpOrder_rejectsFractionalAmount() {
            assertThrows(BusinessException.class,
                    () -> service.createTopUpOrder(new BigDecimal("10000.50")));

            verify(orderRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 38 — getOrderForCurrentStudent (UC-09 View Purchase History) — 4 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 38 - getOrderForCurrentStudent (UC-09)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GetOrderForCurrentStudent {

        @Test
        @org.junit.jupiter.api.Order(1)
        @DisplayName("UTCID01 (N) - own order -> detail with items")
        void getOrderForCurrentStudent_ownOrder_returnsDetailWithItems() {
            Order order = paidOrder();
            OrderItem item = OrderItem.builder().order(order).course(course).price(course.getPrice()).build();
            OrderResponse expected = response(order);
            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrder_Id(order.getId())).thenReturn(List.of(item));
            when(orderMapper.toResponse(order, List.of(item))).thenReturn(expected);

            OrderResponse actual = service.getOrderForCurrentStudent(order.getId());

            assertEquals(expected, actual);
            verify(orderItemRepository).findByOrder_Id(order.getId());
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        @DisplayName("UTCID02 (A) - order of another student -> ORDER_NOT_FOUND")
        void getOrderForCurrentStudent_orderOwnedByAnotherStudent_isHiddenAsNotFound() {
            Order order = paidOrder();
            order.setStudent(StudentProfile.builder().id(UUID.randomUUID()).build());
            when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

            assertThrows(BusinessException.class,
                    () -> service.getOrderForCurrentStudent(order.getId()));
            verify(orderMapper, never()).toResponse(any(), any());
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        @DisplayName("UTCID03 (A) - unknown order id -> ORDER_NOT_FOUND")
        void getOrderForCurrentStudent_orderNotFound_throws() {
            UUID unknownId = UUID.randomUUID();
            when(orderRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                    () -> service.getOrderForCurrentStudent(unknownId));
            verify(orderMapper, never()).toResponse(any(), any());
        }

        @Test
        @org.junit.jupiter.api.Order(4)
        @DisplayName("UTCID04 (A) - no student profile -> LEARNING_STUDENT_PROFILE_NOT_FOUND")
        void getOrderForCurrentStudent_studentProfileMissing_throws() {
            when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                    () -> service.getOrderForCurrentStudent(UUID.randomUUID()));
            verify(orderRepository, never()).findById(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sheet 39 — getOrdersForCurrentStudent (UC-09 View Purchase History) — 5 TC
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet 39 - getOrdersForCurrentStudent (UC-09)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GetOrdersForCurrentStudent {

        @Test
        @org.junit.jupiter.api.Order(1)
        @DisplayName("UTCID01 (N) - status PAID -> filtered in the repository query")
        void getOrdersForCurrentStudent_filtersServerSideAndBatchesItems() {
            UUID orderId = UUID.randomUUID();
            Order order = Order.builder()
                    .id(orderId)
                    .student(student)
                    .orderCode("OD202607270001")
                    .totalAmount(course.getPrice())
                    .currency("VND")
                    .status(OrderStatus.PAID)
                    .createdAt(Instant.parse("2026-07-27T00:00:00Z"))
                    .build();
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .course(course)
                    .price(course.getPrice())
                    .build();
            OrderResponse response = new OrderResponse(
                    orderId,
                    order.getOrderCode(),
                    order.getTotalAmount(),
                    order.getWalletAmount(),
                    order.getCurrency(),
                    order.getStatus().name(),
                    order.getType().name(),
                    order.getCreatedAt(),
                    List.of());
            PageRequest pageable = PageRequest.of(0, 10);

            when(orderRepository.findByStudent_IdAndStatus(student.getId(), OrderStatus.PAID, pageable))
                    .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
            when(orderItemRepository.findByOrder_IdIn(List.of(orderId))).thenReturn(List.of(item));
            when(orderMapper.toResponse(order, List.of(item))).thenReturn(response);

            PageResponse<OrderResponse> result =
                    service.getOrdersForCurrentStudent(OrderStatus.PAID, pageable);

            assertEquals(1, result.getTotalElements());
            assertEquals(orderId, result.getContent().getFirst().id());
            verify(orderRepository).findByStudent_IdAndStatus(student.getId(), OrderStatus.PAID, pageable);
            verify(orderItemRepository).findByOrder_IdIn(List.of(orderId));
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        @DisplayName("UTCID02 (N) - status null -> every order of the student")
        void getOrdersForCurrentStudent_nullStatus_listsEveryOrderOfTheStudent() {
            Order order = paidOrder();
            OrderItem item = OrderItem.builder().order(order).course(course).price(course.getPrice()).build();
            PageRequest pageable = PageRequest.of(0, 10);
            when(orderRepository.findByStudent_Id(student.getId(), pageable))
                    .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
            when(orderItemRepository.findByOrder_IdIn(List.of(order.getId()))).thenReturn(List.of(item));
            when(orderMapper.toResponse(order, List.of(item))).thenReturn(response(order));

            PageResponse<OrderResponse> result = service.getOrdersForCurrentStudent(null, pageable);

            assertEquals(1, result.getTotalElements());
            verify(orderRepository).findByStudent_Id(student.getId(), pageable);
            verify(orderRepository, never()).findByStudent_IdAndStatus(any(), any(), any());
        }

        @Test
        @org.junit.jupiter.api.Order(3)
        @DisplayName("UTCID03 (N) - 2 orders -> items loaded in one batch query")
        void getOrdersForCurrentStudent_batchesItemsOfEveryOrderInOneQuery() {
            Order first = paidOrder();
            Order second = paidOrder();
            OrderItem firstItem = OrderItem.builder().order(first).course(course).price(course.getPrice()).build();
            OrderItem secondItem = OrderItem.builder().order(second).course(course).price(course.getPrice()).build();
            PageRequest pageable = PageRequest.of(0, 10);
            when(orderRepository.findByStudent_Id(student.getId(), pageable))
                    .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
            when(orderItemRepository.findByOrder_IdIn(List.of(first.getId(), second.getId())))
                    .thenReturn(List.of(firstItem, secondItem));
            when(orderMapper.toResponse(first, List.of(firstItem))).thenReturn(response(first));
            when(orderMapper.toResponse(second, List.of(secondItem))).thenReturn(response(second));

            PageResponse<OrderResponse> result = service.getOrdersForCurrentStudent(null, pageable);

            assertEquals(2, result.getTotalElements());
            verify(orderItemRepository).findByOrder_IdIn(List.of(first.getId(), second.getId()));
        }

        @Test
        @org.junit.jupiter.api.Order(4)
        @DisplayName("UTCID04 (B) - no matching order -> empty page, batch query skipped")
        void getOrdersForCurrentStudent_emptyPage_skipsItemBatchLookup() {
            PageRequest pageable = PageRequest.of(0, 10);
            when(orderRepository.findByStudent_IdAndStatus(student.getId(), OrderStatus.REFUNDED, pageable))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            PageResponse<OrderResponse> result =
                    service.getOrdersForCurrentStudent(OrderStatus.REFUNDED, pageable);

            assertEquals(0, result.getTotalElements());
            assertEquals(List.of(), result.getContent());
            verify(orderItemRepository, never()).findByOrder_IdIn(any());
        }

        @Test
        @org.junit.jupiter.api.Order(5)
        @DisplayName("UTCID05 (A) - no student profile -> LEARNING_STUDENT_PROFILE_NOT_FOUND")
        void getOrdersForCurrentStudent_studentProfileMissing_throws() {
            when(studentProfileRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                    () -> service.getOrdersForCurrentStudent(null, PageRequest.of(0, 10)));
            verify(orderRepository, never()).findByStudent_Id(any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Not part of Report 5.1 — kept from the earlier iteration
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("(khong thuoc sheet nao) - enrollFreeOrder")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class EnrollFreeOrder {

        @Test
        @org.junit.jupiter.api.Order(1)
        void enrollFreeOrder_createsEnrollmentAndMarksOrderPaid() {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .orderCode("OD-FREE")
                    .totalAmount(BigDecimal.ZERO)
                    .currency("VND")
                    .status(OrderStatus.PENDING)
                    .student(student)
                    .build();
            when(orderItemRepository.findByOrder_Id(order.getId())).thenReturn(java.util.List.of(
                    OrderItem.builder().order(order).course(course).price(BigDecimal.ZERO).build()));
            when(enrollmentRepository.findByStudentIdAndCourseIdForUpdate(student.getId(), course.getId()))
                    .thenReturn(Optional.empty());

            service.enrollFreeOrder(order);

            assertEquals(OrderStatus.PAID, order.getStatus());
            verify(enrollmentRepository).save(any(Enrollment.class));
            verify(orderRepository).save(order);
        }

        @Test
        @org.junit.jupiter.api.Order(2)
        void enrollFreeOrder_resetsRefundedEnrollmentWithoutReplacingIt() {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .orderCode("OD-FREE-REPURCHASE")
                    .totalAmount(BigDecimal.ZERO)
                    .currency("VND")
                    .status(OrderStatus.PENDING)
                    .student(student)
                    .build();
            Enrollment refunded = Enrollment.builder()
                    .id(UUID.randomUUID())
                    .student(student)
                    .course(course)
                    .status(EnrollmentStatus.REFUNDED)
                    .build();
            UUID originalEnrollmentId = refunded.getId();
            when(orderItemRepository.findByOrder_Id(order.getId())).thenReturn(List.of(
                    OrderItem.builder().order(order).course(course).price(BigDecimal.ZERO).build()));
            when(enrollmentRepository.findByStudentIdAndCourseIdForUpdate(student.getId(), course.getId()))
                    .thenReturn(Optional.of(refunded));

            service.enrollFreeOrder(order);

            assertEquals(originalEnrollmentId, refunded.getId());
            verify(enrollmentProgressResetService).resetForRepurchase(refunded);
            verify(orderRepository).save(order);
        }
    }

    private Order paidOrder() {
        return Order.builder()
                .id(UUID.randomUUID())
                .student(student)
                .orderCode("OD202608060001")
                .totalAmount(course.getPrice())
                .currency("VND")
                .status(OrderStatus.PAID)
                .createdAt(Instant.parse("2026-08-06T00:00:00Z"))
                .build();
    }

    private OrderResponse response(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getTotalAmount(),
                order.getWalletAmount(),
                order.getCurrency(),
                order.getStatus().name(),
                order.getType().name(),
                order.getCreatedAt(),
                List.of());
    }
}
