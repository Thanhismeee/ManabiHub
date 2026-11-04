package com.manabihub.kyc.service;

import com.manabihub.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VnptSdkPayloadPolicyTest {

    @Test
    void acceptsCompactedVnptEvidence() {
        Map<String, Object> payload = Map.of(
                "ocr", Map.of("object", Map.of(
                        "id", "012345678901",
                        "name", "Nguyen Van A",
                        "birth_day", "02/01/1990"
                )),
                "liveness_face", Map.of("object", Map.of("liveness", "success", "prob", 0.98D)),
                "general_warning", List.of()
        );

        assertDoesNotThrow(() -> VnptSdkPayloadPolicy.validate(payload));
    }

    @Test
    void rejectsCredentialsEvenWhenFrontendCompactionIsBypassed() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> VnptSdkPayloadPolicy.validate(Map.of(
                        "ocr", Map.of("authorization", "Bearer must-not-reach-storage")
                )));

        assertEquals(400, exception.getHttpStatus().value());
    }

    @Test
    void rejectsOversizedCollectionsAndDeepObjects() {
        List<Integer> tooManyItems = new ArrayList<>();
        for (int index = 0; index < 51; index++) {
            tooManyItems.add(index);
        }
        assertThrows(BusinessException.class,
                () -> VnptSdkPayloadPolicy.validate(Map.of("warnings", tooManyItems)));

        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> cursor = root;
        for (int depth = 0; depth < 10; depth++) {
            Map<String, Object> child = new LinkedHashMap<>();
            cursor.put("nested", child);
            cursor = child;
        }
        assertThrows(BusinessException.class, () -> VnptSdkPayloadPolicy.validate(root));
    }

    @Test
    void rejectsEmbeddedCccdMedia() {
        assertThrows(BusinessException.class,
                () -> VnptSdkPayloadPolicy.validate(Map.of(
                        "portrait", "data:image/jpeg;base64,AAAA"
                )));
    }
}
