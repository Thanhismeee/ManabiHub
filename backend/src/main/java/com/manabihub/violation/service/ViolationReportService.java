package com.manabihub.violation.service;

import com.manabihub.violation.dto.ViolationReportRequest;
import com.manabihub.violation.dto.ViolationReportResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ViolationReportService {
    ViolationReportResponse submitReport(ViolationReportRequest request, UUID reporterId);

    ViolationReportResponse submitReport(
            ViolationReportRequest request,
            List<MultipartFile> evidenceFiles,
            UUID reporterId
    );
}
