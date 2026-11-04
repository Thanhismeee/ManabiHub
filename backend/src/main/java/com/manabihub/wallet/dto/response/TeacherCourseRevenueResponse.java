package com.manabihub.wallet.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/** Revenue breakdown for one course in the teacher's revenue wallet. */
@Value
@Builder
public class TeacherCourseRevenueResponse {
    UUID courseId;
    String courseTitle;
    long purchaseCount;
    long refundedCount;
    BigDecimal grossRevenue;
    BigDecimal teacherNetRevenue;
    BigDecimal heldAmount;
    BigDecimal releasedAmount;
    BigDecimal refundedAmount;
}
