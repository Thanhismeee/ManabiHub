package com.manabihub.identity.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.config.PhoneVerificationSmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * SMS delivery adapter. Local/demo can log the generated message, legacy
 * deployments can use the generic webhook, and production can call eSMS
 * directly using its OTP/CSKH JSON contract.
 */
@Service
@Slf4j
public class ConfigurableSmsSender implements SmsSender {

    static final URI ESMS_ENDPOINT = URI.create(
            "https://rest.esms.vn/MainService.svc/json/SendMultipleMessage_V4_post_json/"
    );

    private final RestClient restClient;
    private final PhoneVerificationSmsProperties properties;

    @Autowired
    public ConfigurableSmsSender(
            RestClient.Builder restClientBuilder,
            PhoneVerificationSmsProperties properties
    ) {
        this(buildClient(restClientBuilder, properties), properties);
    }

    ConfigurableSmsSender(RestClient restClient, PhoneVerificationSmsProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public void send(String phoneNumber, String message) {
        String mode = normalizeMode(properties.getSmsMode());
        if ("console".equals(mode)) {
            log.warn("PHONE VERIFICATION DEMO SMS to {}: {}", mask(phoneNumber), message);
            return;
        }

        try {
            if ("webhook".equals(mode)) {
                sendWebhook(phoneNumber, message);
                return;
            }
            if ("esms".equals(mode)) {
                sendEsms(phoneNumber, message);
                return;
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn(
                    "SMS provider request failed for {} (provider={}, failure={})",
                    mask(phoneNumber),
                    mode,
                    exception.getClass().getSimpleName()
            );
            throw deliveryFailed();
        }

        throw notConfigured();
    }

    private void sendWebhook(String phoneNumber, String message) {
        if (!StringUtils.hasText(properties.getSmsWebhookUrl())
                || !StringUtils.hasText(properties.getSmsApiKey())) {
            throw notConfigured();
        }

        restClient
                .post()
                .uri(properties.getSmsWebhookUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getSmsApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("to", phoneNumber, "message", message))
                .retrieve()
                .toBodilessEntity();
    }

    private void sendEsms(String phoneNumber, String message) {
        PhoneVerificationSmsProperties.Esms esms = properties.getEsms();
        if (esms == null
                || !StringUtils.hasText(esms.getApiKey())
                || !StringUtils.hasText(esms.getSecretKey())
                || !StringUtils.hasText(esms.getBrandname())) {
            throw notConfigured();
        }

        String requestId = UUID.randomUUID().toString();
        Map<String, String> request = new LinkedHashMap<>();
        request.put("ApiKey", esms.getApiKey());
        request.put("SecretKey", esms.getSecretKey());
        request.put("Content", message);
        request.put("Phone", phoneNumber);
        request.put("Brandname", esms.getBrandname());
        request.put("SmsType", "2");
        request.put("IsUnicode", "0");
        request.put("Sandbox", esms.isSandbox() ? "1" : "0");
        request.put("RequestId", requestId);

        EsmsResponse response = restClient
                .post()
                .uri(ESMS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(EsmsResponse.class);

        if (response == null
                || !"100".equals(response.codeResult())
                || !StringUtils.hasText(response.smsId())) {
            log.warn(
                    "eSMS rejected phone verification message for {} (requestId={}, providerCode={})",
                    mask(phoneNumber),
                    requestId,
                    safeProviderCode(response == null ? null : response.codeResult())
            );
            throw deliveryFailed();
        }

        log.info(
                "eSMS accepted phone verification message for {} (requestId={})",
                mask(phoneNumber),
                requestId
        );
    }

    private static RestClient buildClient(
            RestClient.Builder restClientBuilder,
            PhoneVerificationSmsProperties properties
    ) {
        int timeoutMillis = Math.max(1, properties.getSmsTimeoutSeconds()) * 1_000;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        return restClientBuilder.requestFactory(requestFactory).build();
    }

    private String normalizeMode(String mode) {
        return mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
    }

    private String safeProviderCode(String providerCode) {
        return providerCode != null && providerCode.matches("\\d{1,6}")
                ? providerCode
                : "invalid";
    }

    private BusinessException notConfigured() {
        return new BusinessException(
                MessageCodes.PHONE_VERIFICATION_SMS_NOT_CONFIGURED,
                "SMS provider is not configured"
        );
    }

    private BusinessException deliveryFailed() {
        return new BusinessException(
                MessageCodes.PHONE_VERIFICATION_SMS_DELIVERY_FAILED,
                "The verification message could not be accepted by the SMS provider",
                HttpStatus.BAD_GATEWAY
        );
    }

    private String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return "******" + phoneNumber.substring(phoneNumber.length() - 4);
    }

    private record EsmsResponse(
            @JsonProperty("CodeResult") String codeResult,
            @JsonProperty("SMSID") String smsId
    ) {
    }
}
