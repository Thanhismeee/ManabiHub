package com.manabihub.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonNameNormalizerTest {

    @Test
    void matchesTheSameNameWithAndWithoutDiacritics() {
        assertEquals(PersonNameNormalizer.normalize("Nguyễn Xuân Đạt"),
                PersonNameNormalizer.normalize("NGUYEN XUAN DAT"));
    }

    @Test
    void ignoresCaseSpacingAndPunctuation() {
        assertEquals(PersonNameNormalizer.normalize("  tran  van-khac "),
                PersonNameNormalizer.normalize("TRAN VAN KHAC"));
    }

    @Test
    void distinguishesDifferentPeople() {
        assertNotEquals(PersonNameNormalizer.normalize("NGUYEN XUAN DAT"),
                PersonNameNormalizer.normalize("TRAN VAN KHAC"));
    }

    @Test
    void mapsNullAndBlankToEmpty() {
        assertTrue(PersonNameNormalizer.normalize(null).isBlank());
        assertTrue(PersonNameNormalizer.normalize("   ").isBlank());
    }
}