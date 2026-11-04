package com.manabihub.oversight.dto.request;

import com.manabihub.oversight.enums.DecisionDomain;
import com.manabihub.oversight.enums.DecisionReviewStatus;
import com.manabihub.oversight.enums.DecisionWarningLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

@Getter
@Setter
public class DecisionReviewFilterRequest {
    private DecisionDomain domain;
    private String decisionRole;
    private String actor;
    private DecisionReviewStatus reviewStatus;
    private DecisionWarningLevel warningLevel;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant to;
}
