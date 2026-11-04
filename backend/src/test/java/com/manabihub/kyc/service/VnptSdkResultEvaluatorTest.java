package com.manabihub.kyc.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnptSdkResultEvaluatorTest {

    @Test
    void terminalFailureOverridesEarlierDocumentAndFaceSuccess() {
        for (String terminalStatus : new String[]{
                "CANCEL", "CANCELLED", "CANCELED", "USER_CANCELLED",
                "ABORT", "ABORTED", "TIMEOUT", "TIMED_OUT",
                "ERROR", "ERROR_PROVIDER", "NETWORK_ERROR"
        }) {
            VnptSdkDecision decision = VnptSdkResultEvaluator.evaluate(
                    payloadWithTerminalStatus(terminalStatus));

            assertFalse(decision.verified(), terminalStatus);
        }
    }

    @Test
    void explicitNoErrorStatusDoesNotOverrideSuccessfulEvidence() {
        VnptSdkDecision decision = VnptSdkResultEvaluator.evaluate(
                payloadWithTerminalStatus("NO_ERROR"));

        assertTrue(decision.verified());
    }

    @Test
    void acceptsDocumentQualityWarningsWhenTheDocumentAndFaceSignalsPass() {
        VnptSdkDecision decision = VnptSdkResultEvaluator.evaluate(Map.of(
                "documentResult", Map.of(
                        "ocr", Map.of(
                                "id", "012345678901",
                                "name", "NGUYEN VAN A",
                                "birth_day", "01/01/2000",
                                "msg", "OK",
                                "msg_back", "OK",
                                "warning", List.of("anh_dau_vao_mo_nhoe"),
                                "warning_msg", "Anh dau vao hoi mo",
                                "id_fake_warning", "no",
                                "id_fake_prob", 0.98,
                                "tampering", Map.of("is_legal", "yes", "warning", List.of())),
                        "liveness_card_front", Map.of("liveness", "success", "fake_liveness", false),
                        "liveness_card_back", Map.of("liveness", "success", "face_swapping", false)),
                "callbackResult", Map.of(
                        "liveness_face", Map.of("liveness", "success"),
                        "masked", "no",
                        "compare", Map.of("msg", "MATCH", "prob", 98)),
                "endFlowResult", Map.of("status", "SUCCESS")));

        assertTrue(decision.verified(), () -> String.join("; ", decision.failureReasons()));
    }

    @Test
    void acceptsSuccessfulVendorEndFlowPayloadWithNormalNegativeSafetyFlags() {
        VnptSdkDecision decision = VnptSdkResultEvaluator.evaluate(Map.of(
                "endFlowResult", Map.of(
                        "client_session", "WEB-SDK_chrome_3.2.1_session",
                        "liveness_card_front", Map.of(
                                "object", Map.of("liveness", "success", "fake_liveness", false)),
                        "liveness_card_back", Map.of(
                                "object", Map.of("liveness", "success", "face_swapping", false)),
                        "ocr", Map.of("object", Map.of(
                                "id", "012345678901",
                                "name", "NGUYEN VAN A",
                                "birth_day", "01/01/2000",
                                "msg", "OK",
                                "msg_back", "OK",
                                "id_fake_warning", "no",
                                "tampering", Map.of("is_legal", "yes", "warning", List.of()))),
                        "liveness_face", Map.of("object", Map.of("liveness", "success")),
                        "masked", Map.of("object", Map.of("masked", "no")),
                        "compare", Map.of("object", Map.of("msg", "MATCH", "prob", 98)))));

        assertTrue(decision.verified(), () -> String.join("; ", decision.failureReasons()));
    }

    @Test
    void rejectsNestedTerminalFailureWithinVendorEnvelope() {
        VnptSdkDecision decision = VnptSdkResultEvaluator.evaluate(Map.of(
                "documentResult", Map.of(
                        "idNumber", "012345678901",
                        "fullName", "NGUYEN VAN A",
                        "dateOfBirth", "01/01/2000"),
                "callbackResult", Map.of(
                        "faceLiveness", Map.of("status", "SUCCESS"),
                        "faceCompare", Map.of("result", "MATCH")),
                "endFlowResult", Map.of("object", Map.of("status", "CANCEL"))));

        assertFalse(decision.verified());
        assertTrue(decision.failureReasons().contains("VNPT terminal flow did not complete successfully"));
    }

    @Test
    void rejectsOnlyDocumentedHardFailureSignals() {
        assertFalse(VnptSdkResultEvaluator.evaluate(withOcr(Map.of(
                "compare", Map.of("msg", "NOMATCH")))).verified());
        assertFalse(VnptSdkResultEvaluator.evaluate(withOcr(Map.of(
                "ocr", Map.of("tampering", Map.of("is_legal", "no"))))).verified());
        assertFalse(VnptSdkResultEvaluator.evaluate(withOcr(Map.of(
                "liveness_face", Map.of("liveness", "failure")))).verified());
        assertFalse(VnptSdkResultEvaluator.evaluate(withOcr(Map.of(
                "ocr", Map.of("id_fake_warning", "yes")))).verified());
    }

    private Map<String, Object> payloadWithTerminalStatus(String status) {
        return Map.of(
                "documentResult", Map.of(
                        "idNumber", "027204002711",
                        "fullName", "NGUYEN XUAN DAT",
                        "dateOfBirth", "2004-08-31"),
                "callbackResult", Map.of(
                        "faceLiveness", Map.of("status", "SUCCESS"),
                        "faceCompare", Map.of("result", "MATCH")),
                "endFlowResult", Map.of("status", status));
    }

    private Map<String, Object> withOcr(Map<String, Object> additionalCallbackResult) {
        return Map.of(
                "documentResult", Map.of(
                        "ocr", Map.of(
                                "id", "012345678901",
                                "name", "NGUYEN VAN A",
                                "birth_day", "01/01/2000",
                                "msg", "OK",
                                "msg_back", "OK")),
                "callbackResult", Map.of(
                        "liveness_face", Map.of("liveness", "success"),
                        "compare", Map.of("msg", "MATCH"),
                        "additional", additionalCallbackResult),
                "endFlowResult", Map.of("status", "SUCCESS"));
    }
}
