package com.manabihub.refund.dto;

import com.manabihub.refund.enums.EligibilityResult;
import com.manabihub.refund.enums.StudentRefundType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RefundEligibilitySnapshot {
    private String snapshotVersion;
    
    @JsonAlias("policyVersion")
    private String policyVersion;
    private StudentRefundType refundType;
    
    @JsonAlias("paymentTime")
    private Instant paymentSucceededAt;
    
    @JsonAlias("requestTime")
    private Instant requestedAt;
    private String timezone;
    private Integer elapsedCalendarDays;
    private Integer refundWindowDays;
    private Integer progressCompleted;
    private Integer progressTotal;
    
    @JsonAlias("progressPercent")
    private Double measuredProgressPercent;
    
    private Integer progressThresholdPercent;
    private Boolean protectedMaterialsFullyDownloaded;
    private Instant protectedMaterialsFullyDownloadedAt;
    
    @JsonAlias("paidAmount")
    private BigDecimal actuallyPaidAmount;
    private String currency;
    private UUID orderId;
    private UUID orderItemId;
    private UUID courseId;
    
    private Boolean eligible;
    private String result;
    
    private EligibilityResult eligibilityResult;
    private List<String> reasonCodes;
}
