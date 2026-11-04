package com.manabihub.operations.service;

import com.manabihub.operations.dto.OperationsOverviewResponse;
import com.manabihub.operations.dto.RecentApplicationLogResponse;
import com.manabihub.operations.dto.RuntimeConfigurationResponse;

public interface OperationsService {

    OperationsOverviewResponse getOverview();

    RuntimeConfigurationResponse getRuntimeConfiguration();

    RecentApplicationLogResponse getRecentLogs(
            String level,
            String query,
            String correlationId,
            int limit
    );
}
