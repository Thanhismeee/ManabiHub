package com.manabihub.course.domain;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.course.enums.JlptLevel;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.course.enums.ProficiencyFramework;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/** Normalizes the new language fields while preserving the legacy JLPT API. */
public record CourseLanguageProfile(
        LearningLanguage learningLanguage,
        ProficiencyFramework proficiencyFramework,
        String frameworkVersion,
        String proficiencyLevel,
        JlptLevel legacyJlptLevel
) {
    private static final Set<String> JLPT_LEVELS = Set.of("N1", "N2", "N3", "N4", "N5");
    private static final Set<String> TOPIK_LEVELS = Set.of("1", "2", "3", "4", "5", "6");
    private static final Set<String> HSK_LEVELS = Set.of("1", "2", "3", "4", "5", "6", "7", "8", "9");

    public static CourseLanguageProfile resolve(
            LearningLanguage language,
            ProficiencyFramework framework,
            String version,
            String level,
            JlptLevel legacyJlptLevel
    ) {
        LearningLanguage resolvedLanguage = language;
        ProficiencyFramework resolvedFramework = framework;
        String resolvedLevel = normalize(level);

        if (resolvedLanguage == null && legacyJlptLevel != null) {
            resolvedLanguage = LearningLanguage.JAPANESE;
        }
        if (resolvedLanguage == null) {
            throw invalid("Vui lòng chọn ngôn ngữ của khóa học.");
        }

        if (resolvedFramework == null) {
            resolvedFramework = defaultFramework(resolvedLanguage);
        }
        validateFramework(resolvedLanguage, resolvedFramework);

        if (!StringUtils.hasText(resolvedLevel) && legacyJlptLevel != null) {
            resolvedLevel = legacyJlptLevel.name();
        }
        if (!StringUtils.hasText(resolvedLevel)) {
            throw invalid("Vui lòng chọn cấp độ của khóa học.");
        }

        resolvedLevel = resolvedLevel.toUpperCase(Locale.ROOT);
        if (resolvedFramework == ProficiencyFramework.TOPIK) resolvedLevel = stripPrefix(resolvedLevel, "TOPIK");
        if (resolvedFramework == ProficiencyFramework.HSK) resolvedLevel = stripPrefix(resolvedLevel, "HSK");
        validateLevel(resolvedFramework, resolvedLevel);

        JlptLevel mirroredJlpt = null;
        if (resolvedLanguage == LearningLanguage.JAPANESE
                && resolvedFramework == ProficiencyFramework.JLPT) {
            mirroredJlpt = JlptLevel.valueOf(resolvedLevel);
            if (legacyJlptLevel != null && legacyJlptLevel != mirroredJlpt) {
                throw invalid("Cấp độ JLPT cũ và cấp độ mới không khớp.");
            }
        } else if (legacyJlptLevel != null) {
            throw invalid("Trường jlptLevel chỉ được dùng cho khóa tiếng Nhật theo JLPT.");
        }

        String resolvedVersion = normalize(version);
        if (!StringUtils.hasText(resolvedVersion)) {
            resolvedVersion = "CURRENT";
        }
        if (resolvedVersion.length() > 32 || resolvedLevel.length() > 32) {
            throw invalid("Phiên bản hoặc cấp độ không được vượt quá 32 ký tự.");
        }

        return new CourseLanguageProfile(
                resolvedLanguage,
                resolvedFramework,
                resolvedVersion,
                resolvedLevel,
                mirroredJlpt
        );
    }

    private static ProficiencyFramework defaultFramework(LearningLanguage language) {
        return switch (language) {
            case JAPANESE -> ProficiencyFramework.JLPT;
            case KOREAN -> ProficiencyFramework.TOPIK;
            case CHINESE_SIMPLIFIED -> ProficiencyFramework.HSK;
        };
    }

    private static void validateFramework(LearningLanguage language, ProficiencyFramework framework) {
        boolean valid = framework == ProficiencyFramework.SOFL_INTERNAL
                || language == LearningLanguage.JAPANESE && framework == ProficiencyFramework.JLPT
                || language == LearningLanguage.KOREAN && framework == ProficiencyFramework.TOPIK
                || language == LearningLanguage.CHINESE_SIMPLIFIED && framework == ProficiencyFramework.HSK;
        if (!valid) {
            throw invalid("Khung trình độ không phù hợp với ngôn ngữ đã chọn.");
        }
    }

    private static void validateLevel(ProficiencyFramework framework, String level) {
        boolean valid = switch (framework) {
            case JLPT -> JLPT_LEVELS.contains(level);
            case TOPIK -> TOPIK_LEVELS.contains(stripPrefix(level, "TOPIK"));
            case HSK -> HSK_LEVELS.contains(stripPrefix(level, "HSK"));
            case SOFL_INTERNAL -> level.matches("[A-Z0-9][A-Z0-9_.-]{0,31}");
        };
        if (!valid) {
            throw invalid("Cấp độ không hợp lệ cho khung trình độ đã chọn.");
        }
    }

    private static String stripPrefix(String level, String prefix) {
        String normalized = level.replace("_", "").replace("-", "").replace(" ", "");
        return normalized.startsWith(prefix) ? normalized.substring(prefix.length()) : normalized;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(
                MessageCodes.VALIDATION_FAILED,
                message,
                HttpStatus.BAD_REQUEST
        );
    }
}
