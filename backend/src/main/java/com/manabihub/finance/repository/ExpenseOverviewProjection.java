package com.manabihub.finance.repository;

import java.math.BigDecimal;

public interface ExpenseOverviewProjection {
    Long getTotalDocuments();

    BigDecimal getTotalConfirmedVnd();

    Long getDraftCount();

    Long getConfirmedCount();

    Long getPaidCount();

    Long getOverdueCount();
}
