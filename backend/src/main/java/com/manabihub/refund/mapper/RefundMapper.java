package com.manabihub.refund.mapper;

import com.manabihub.refund.dto.response.RefundDetailResponse;
import com.manabihub.refund.dto.response.RefundQueueResponse;
import com.manabihub.refund.entity.RefundRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RefundMapper {

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "orderCode", source = "order.orderCode")
    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "studentName", source = "student.user.fullName")
    @Mapping(target = "studentEmail", source = "student.user.email")
    @Mapping(target = "decidedBy", source = "decidedBy.id")
    RefundQueueResponse toQueueResponse(RefundRequest entity);

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "orderCode", source = "order.orderCode")
    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "studentName", source = "student.user.fullName")
    @Mapping(target = "studentEmail", source = "student.user.email")
    @Mapping(target = "decidedBy", source = "decidedBy.id")
    RefundDetailResponse toDetailResponse(RefundRequest entity);
}
