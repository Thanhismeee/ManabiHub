package com.manabihub.violation.mapper;

import com.manabihub.violation.dto.ViolationReportResponse;
import com.manabihub.violation.entity.ViolationReport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ViolationReportMapper {

    @Mapping(source = "reporter.id", target = "reporterId")
    ViolationReportResponse toResponse(ViolationReport entity);
}
