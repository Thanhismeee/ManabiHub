package com.manabihub.course.enums;

/** Language being learned. This is independent from the UI locale. */
public enum LearningLanguage {
    JAPANESE("ja"),
    KOREAN("ko"),
    CHINESE_SIMPLIFIED("zh-Hans");

    private final String languageTag;

    LearningLanguage(String languageTag) {
        this.languageTag = languageTag;
    }

    public String getLanguageTag() {
        return languageTag;
    }
}
