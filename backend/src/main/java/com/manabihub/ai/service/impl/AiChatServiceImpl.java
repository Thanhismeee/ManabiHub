package com.manabihub.ai.service.impl;

import com.manabihub.ai.domain.AiChatContext;
import com.manabihub.ai.domain.AiChatEligibility;
import com.manabihub.ai.dto.request.AiChatMessageRequest;
import com.manabihub.ai.dto.response.AiChatEligibilityResponse;
import com.manabihub.ai.dto.response.AiChatMessageResponse;
import com.manabihub.ai.enums.AiUsageRequestStatus;
import com.manabihub.ai.provider.AiChatProvider;
import com.manabihub.ai.provider.AiChatProviderResult;
import com.manabihub.ai.service.AiChatContextBuilder;
import com.manabihub.ai.service.AiChatGuardrail;
import com.manabihub.ai.service.AiChatService;
import com.manabihub.ai.service.AiChatSettingsService;
import com.manabihub.ai.service.AiUsageLogService;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.entity.Course;
import com.manabihub.course.entity.LessonBlock;
import com.manabihub.course.repository.CourseRepository;
import com.manabihub.course.repository.LessonBlockRepository;
import com.manabihub.identity.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiChatServiceImpl implements AiChatService {

    private static final String AI_CHATBOT_FEATURE = "AI_CHATBOT";

    private final CurrentUserService currentUserService;
    private final CourseRepository courseRepository;
    private final LessonBlockRepository lessonBlockRepository;
    private final AiChatSettingsService aiChatSettingsService;
    private final AiChatContextBuilder aiChatContextBuilder;
    private final AiChatGuardrail aiChatGuardrail;
    private final AiChatProvider aiChatProvider;
    private final AiUsageLogService aiUsageLogService;
    private final com.manabihub.ai.service.AiQuotaService aiQuotaService;

    @Override
    public AiChatEligibilityResponse getEligibility(UUID courseId, UUID lessonBlockId) {
        UUID currentUserId = requireCurrentUserId();
        Session session = loadSession(courseId, lessonBlockId);
        AiChatEligibility eligibility = evaluateEligibility(currentUserId, session.course()).eligibility();

        return new AiChatEligibilityResponse(
                eligibility.eligible(),
                eligibility.messageCode(),
                eligibility.message()
        );
    }

    @Override
    @Transactional
    public AiChatMessageResponse sendMessage(UUID courseId, UUID lessonBlockId, AiChatMessageRequest request) {
        UUID currentUserId = requireCurrentUserId();
        Session session = loadSession(courseId, lessonBlockId);
        EligibilityEvaluation evaluation = evaluateEligibility(currentUserId, session.course());

        if (!evaluation.eligibility().eligible()) {
            aiUsageLogService.record(
                    currentUserId,
                    courseId,
                    lessonBlockId,
                    AiUsageRequestStatus.BLOCKED,
                    null,
                    null,
                    null,
                    "INELIGIBLE"
            );
            throw new BusinessException(
                    evaluation.eligibility().messageCode(),
                    evaluation.eligibility().message(),
                    HttpStatus.FORBIDDEN
            );
        }

        if (aiChatGuardrail.blocks(request.question())) {
            aiUsageLogService.record(
                    currentUserId,
                    courseId,
                    lessonBlockId,
                    AiUsageRequestStatus.BLOCKED,
                    null,
                    null,
                    null,
                    "GUARDRAIL"
            );
            throw new BusinessException(
                    MessageCodes.MSG_AI_005,
                    "This question cannot be processed within the current course context.",
                    HttpStatus.BAD_REQUEST
            );
        }

        AiChatContext context = aiChatContextBuilder.build(session.course(), session.lessonBlock());
        UUID reservation = aiQuotaService.reserve(currentUserId, courseId, lessonBlockId, null, AI_CHATBOT_FEATURE, evaluation.settings());
        AiChatProviderResult result;
        try {
            result = aiChatProvider.generate(context, request.question(), request.history());
        } catch (RuntimeException exception) {
            aiQuotaService.finish(reservation, AiUsageRequestStatus.FAILED, null, null, null, "PROVIDER_UNAVAILABLE");
            throw new BusinessException(
                    MessageCodes.MSG_AI_002,
                    "AI service is temporarily unavailable. Please try again later.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        aiQuotaService.finish(reservation, AiUsageRequestStatus.SUCCESS, result.provider(), result.inputTokens(), result.outputTokens(), null);
        return new AiChatMessageResponse(
                courseId,
                lessonBlockId,
                result.answer(),
                MessageCodes.MSG_AI_007,
                result.provider()
        );
    }

    private UUID requireCurrentUserId() {
        return currentUserService.getCurrentUserIdOptional().orElseThrow(() -> new BusinessException(
                MessageCodes.AUTH_UNAUTHORIZED,
                "Authentication is required to use AI chat.",
                HttpStatus.UNAUTHORIZED
        ));
    }

    private Session loadSession(UUID courseId, UUID lessonBlockId) {
        LessonBlock lessonBlock = lessonBlockRepository.findByIdAndCourseId(lessonBlockId, courseId)
                .filter(block -> !block.isModerationHidden())
                .orElseThrow(() -> new BusinessException(
                        MessageCodes.COMMON_NOT_FOUND,
                        "The requested lesson block was not found in this course.",
                        HttpStatus.NOT_FOUND
                ));
        return new Session(lessonBlock.getModule().getCourse(), lessonBlock);
    }

    private EligibilityEvaluation evaluateEligibility(UUID currentUserId, Course course) {
        AiChatSettingsService.AiChatSettings settings = aiChatSettingsService.getSettings();
        if (!courseRepository.checkEnrollmentExists(course.getId(), currentUserId)) {
            return unavailable(settings, "AI chat is available only to actively enrolled students.");
        }
        if (!settings.aiEnabled() || !settings.chatbotEnabled()) {
            return unavailable(settings, "AI chat is currently unavailable for this course.");
        }
        if (!course.isAiSupported()) {
            return unavailable(settings, "This course does not support AI chat.");
        }
        if (isBelowPriceFloor(course.getPrice(), settings.priceFloor())) {
            return unavailable(settings, "AI chat is unavailable for this course plan.");
        }
        return new EligibilityEvaluation(AiChatEligibility.available(), settings);
    }

    private EligibilityEvaluation unavailable(AiChatSettingsService.AiChatSettings settings, String message) {
        return new EligibilityEvaluation(AiChatEligibility.unavailable(MessageCodes.MSG_AI_008, message), settings);
    }

    private boolean isBelowPriceFloor(BigDecimal coursePrice, BigDecimal priceFloor) {
        return coursePrice == null || coursePrice.compareTo(priceFloor) < 0;
    }

    private record Session(Course course, LessonBlock lessonBlock) {
    }

    private record EligibilityEvaluation(
            AiChatEligibility eligibility,
            AiChatSettingsService.AiChatSettings settings
    ) {
    }
}
