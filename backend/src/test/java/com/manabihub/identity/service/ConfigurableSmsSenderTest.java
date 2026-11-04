package com.manabihub.identity.service;

import com.manabihub.common.exception.BusinessException;
import com.manabihub.identity.config.PhoneVerificationSmsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ConfigurableSmsSenderTest {

    private PhoneVerificationSmsProperties properties;
    private MockRestServiceServer server;
    private ConfigurableSmsSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        properties = new PhoneVerificationSmsProperties();
        properties.setSmsMode("esms");
        properties.getEsms().setApiKey("api-key-secret");
        properties.getEsms().setSecretKey("secret-key-secret");
        properties.getEsms().setBrandname("ManabiHub");
        properties.getEsms().setSandbox(false);

        sender = new ConfigurableSmsSender(builder.build(), properties);
    }

    @Test
    void send_EsmsMode_PostsProviderContractAndAcceptsCode100() {
        server.expect(requestTo(ConfigurableSmsSender.ESMS_ENDPOINT))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(jsonPath("$.ApiKey").value("api-key-secret"))
                .andExpect(jsonPath("$.SecretKey").value("secret-key-secret"))
                .andExpect(jsonPath("$.Phone").value("0912345678"))
                .andExpect(jsonPath("$.Content").value(
                        "Ma xac thuc ManabiHub cua ban la 123456. Ma co hieu luc trong 5 phut."
                ))
                .andExpect(jsonPath("$.Brandname").value("ManabiHub"))
                .andExpect(jsonPath("$.SmsType").value("2"))
                .andExpect(jsonPath("$.IsUnicode").value("0"))
                .andExpect(jsonPath("$.Sandbox").value("0"))
                .andExpect(jsonPath("$.RequestId").value(matchesPattern(
                        "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
                )))
                .andRespond(withSuccess(
                        "{\"CodeResult\":\"100\",\"SMSID\":\"provider-message-id\"}",
                        MediaType.APPLICATION_JSON
                ));

        sender.send(
                "0912345678",
                "Ma xac thuc ManabiHub cua ban la 123456. Ma co hieu luc trong 5 phut."
        );

        server.verify();
    }

    @Test
    void send_EsmsMode_RejectsProviderFailureEvenWhenHttpIsSuccessful() {
        server.expect(requestTo(ConfigurableSmsSender.ESMS_ENDPOINT))
                .andRespond(withSuccess(
                        "{\"CodeResult\":\"146\",\"ErrorMessage\":\"template contains api-key-secret\"}",
                        MediaType.APPLICATION_JSON
                ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> sender.send("0912345678", "message-with-otp")
        );

        assertEquals("PHONE_VERIFICATION_SMS_DELIVERY_FAILED", exception.getMessageCode());
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getHttpStatus());
        assertFalse(exception.getMessage().contains("api-key-secret"));
        assertFalse(exception.getMessage().contains("146"));
        server.verify();
    }

    @Test
    void send_EsmsMode_RejectsAcceptedResponseWithoutMessageId() {
        server.expect(requestTo(ConfigurableSmsSender.ESMS_ENDPOINT))
                .andRespond(withSuccess(
                        "{\"CodeResult\":\"100\"}",
                        MediaType.APPLICATION_JSON
                ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> sender.send("0912345678", "message-with-otp")
        );

        assertEquals("PHONE_VERIFICATION_SMS_DELIVERY_FAILED", exception.getMessageCode());
        server.verify();
    }

    @Test
    void send_EsmsMode_MapsHttpFailureToSanitizedDeliveryFailure() {
        server.expect(requestTo(ConfigurableSmsSender.ESMS_ENDPOINT))
                .andRespond(withServerError());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> sender.send("0912345678", "message-with-otp")
        );

        assertEquals("PHONE_VERIFICATION_SMS_DELIVERY_FAILED", exception.getMessageCode());
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getHttpStatus());
        server.verify();
    }

    @Test
    void send_EsmsMode_RejectsMissingCredentialsWithoutCallingProvider() {
        properties.getEsms().setSecretKey(" ");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> sender.send("0912345678", "message-with-otp")
        );

        assertEquals("PHONE_VERIFICATION_SMS_NOT_CONFIGURED", exception.getMessageCode());
        server.verify();
    }

    @Test
    void send_WebhookMode_PreservesLegacyBearerContract() {
        properties.setSmsMode("webhook");
        properties.setSmsWebhookUrl("https://provider.example/v1/messages");
        properties.setSmsApiKey("webhook-key");
        server.expect(requestTo("https://provider.example/v1/messages"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer webhook-key"))
                .andExpect(jsonPath("$.to").value("0912345678"))
                .andExpect(jsonPath("$.message").value("legacy message"))
                .andRespond(withSuccess());

        sender.send("0912345678", "legacy message");

        server.verify();
    }
}
