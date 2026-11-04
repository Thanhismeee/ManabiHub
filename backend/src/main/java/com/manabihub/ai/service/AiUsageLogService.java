package com.manabihub.ai.service;

import com.manabihub.ai.entity.AiUsageLog;
import com.manabihub.ai.enums.AiUsageRequestStatus;
import com.manabihub.ai.repository.AiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiUsageLogService {

    private static final String AI_CHATBOT_FEATURE = "AI_CHATBOT";

    private final AiUsageLogRepository aiUsageLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID userId,
            UUID courseId,
            UUID lessonBlockId,
            AiUsageRequestStatus requestStatus,
            String provider,
            Integer inputTokens,
            Integer outputTokens,
            String failureReason
    ) {
        record(userId, courseId, lessonBlockId, null, AI_CHATBOT_FEATURE, requestStatus, provider, inputTokens, outputTokens, failureReason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID userId,
            UUID courseId,
            UUID lessonBlockId,
            UUID writingSubmissionId,
            String featureCode,
            AiUsageRequestStatus requestStatus,
            String provider,
            Integer inputTokens,
            Integer outputTokens,
            String failureReason
    ) {
        aiUsageLogRepository.save(AiUsageLog.builder()
                .userId(userId)
                .courseId(courseId)
                .lessonBlockId(lessonBlockId)
                .writingSubmissionId(writingSubmissionId)
                .featureCode(featureCode)
                .provider(provider)
                .requestStatus(requestStatus)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .failureReason(failureReason)
                .build());
    }
}
