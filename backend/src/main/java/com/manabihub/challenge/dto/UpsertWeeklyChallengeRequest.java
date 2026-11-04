package com.manabihub.challenge.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UpsertWeeklyChallengeRequest(
        @NotNull LocalDate weekStart,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Pattern(regexp = "N[1-5]") String jlptLevel,
        @Min(value = 1, message = "Số lượt xếp hạng mỗi ngày phải từ 1 đến 10")
        @Max(value = 10, message = "Số lượt xếp hạng mỗi ngày phải từ 1 đến 10") int dailyRankedLimit,
        @Min(value = 0, message = "Thời gian phạt phải từ 0 đến 30 giây")
        @Max(value = 30, message = "Thời gian phạt phải từ 0 đến 30 giây") int wrongPenaltySeconds,
        @NotNull @DecimalMin(value = "0", message = "Thưởng điểm danh phải từ 0 đến 10.000 đồng")
        @DecimalMax(value = "10000", message = "Thưởng điểm danh phải từ 0 đến 10.000 đồng") BigDecimal dailyAttendanceReward,
        @NotNull @DecimalMin(value = "0", message = "Mỗi giải phải từ 0 đến 500.000 đồng")
        @DecimalMax(value = "500000", message = "Mỗi giải phải từ 0 đến 500.000 đồng") BigDecimal firstPrize,
        @NotNull @DecimalMin(value = "0", message = "Mỗi giải phải từ 0 đến 500.000 đồng")
        @DecimalMax(value = "500000", message = "Mỗi giải phải từ 0 đến 500.000 đồng") BigDecimal secondPrize,
        @NotNull @DecimalMin(value = "0", message = "Mỗi giải phải từ 0 đến 500.000 đồng")
        @DecimalMax(value = "500000", message = "Mỗi giải phải từ 0 đến 500.000 đồng") BigDecimal thirdPrize,
        @NotNull @Size(min = 4, max = 12) List<@Valid ChallengePairRequest> pairs
) {}
