package com.manabihub.ai.service;

import com.manabihub.ai.entity.AiUsageLog;
import com.manabihub.ai.enums.AiUsageRequestStatus;
import com.manabihub.ai.repository.AiUsageLogRepository;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class AiQuotaService {
    private final AppUserRepository users;
    private final AiUsageLogRepository logs;
    private static final List<AiUsageRequestStatus> CHARGED = List.of(AiUsageRequestStatus.SUCCESS, AiUsageRequestStatus.PROCESSING, AiUsageRequestStatus.FAILED);
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID reserve(UUID userId, UUID courseId, UUID blockId, UUID writingId, String feature, AiChatSettingsService.AiChatSettings settings) {
        users.findByIdForUpdate(userId).orElseThrow();
        Instant now = Instant.now();
        if (logs.countByUserIdAndRequestStatusInAndCreatedAtAfter(userId, CHARGED, now.minusSeconds(60)) >= settings.rateLimitPerMinute()
                || logs.countByUserIdAndRequestStatusInAndCreatedAtAfter(userId, CHARGED, now.minusSeconds(86400)) >= settings.dailyLimit()) {
            throw new BusinessException(MessageCodes.MSG_AI_001, "Đã đạt định mức AI. Vui lòng chờ trước khi gửi thêm yêu cầu.", HttpStatus.TOO_MANY_REQUESTS);
        }
        return logs.saveAndFlush(AiUsageLog.builder().userId(userId).courseId(courseId).lessonBlockId(blockId)
                .writingSubmissionId(writingId).featureCode(feature).requestStatus(AiUsageRequestStatus.PROCESSING).build()).getId();
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(UUID reservation, AiUsageRequestStatus status, String provider, Integer inputTokens, Integer outputTokens, String failure) {
        AiUsageLog log = logs.findById(reservation).orElseThrow();
        if (log.getRequestStatus() != AiUsageRequestStatus.PROCESSING) return;
        log.setRequestStatus(status); log.setProvider(provider); log.setInputTokens(inputTokens);
        log.setOutputTokens(outputTokens); log.setFailureReason(failure); logs.save(log);
    }
}
