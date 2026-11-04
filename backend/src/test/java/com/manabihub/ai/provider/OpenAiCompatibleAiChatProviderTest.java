package com.manabihub.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.ai.config.AiChatProviderProperties;
import com.manabihub.ai.domain.AiChatContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleAiChatProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorizationHeader = new AtomicReference<>();

    private AiChatProviderProperties properties;
    private HttpServer server;
    private String responseBody;
    private OpenAiCompatibleAiChatProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        responseBody = """
                {
                  "choices": [
                    {"message": {"content": "It marks the topic in this lesson."}}
                  ],
                  "usage": {
                    "prompt_tokens": 42,
                    "completion_tokens": 9
                  }
                }
                """;

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::handleRequest);
        server.start();

        properties = new AiChatProviderProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        properties.setEndpoint("/v1/chat/completions");
        properties.setApiKey("provider-secret");
        properties.setModel("provider-model");
        properties.setTimeoutSeconds(3);

        provider = new OpenAiCompatibleAiChatProvider(RestClient.builder(), properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void generate_SendsScopedContextAndParsesAnswerAndUsage() throws Exception {
        AiChatProviderResult result = provider.generate(context(), "What does は do?", null);

        assertEquals("It marks the topic in this lesson.", result.answer());
        assertEquals("openai-compatible", result.provider());
        assertEquals(42, result.inputTokens());
        assertEquals(9, result.outputTokens());
        assertEquals("Bearer provider-secret", authorizationHeader.get());

        JsonNode request = objectMapper.readTree(requestBody.get());
        assertEquals("provider-model", request.path("model").asText());
        assertEquals(0.2, request.path("temperature").asDouble());
        assertEquals("system", request.path("messages").get(0).path("role").asText());
        assertTrue(request.path("messages").get(0).path("content").asText()
                .contains("Current lesson content"));
        assertTrue(request.path("messages").get(0).path("content").asText()
                .contains("Use は for the topic."));
        assertEquals("user", request.path("messages").get(1).path("role").asText());
        assertEquals("What does は do?", request.path("messages").get(1).path("content").asText());
    }

    @Test
    void generate_WhenProviderReturnsNoAnswer_FailsClosed() {
        responseBody = """
                {"choices": [], "usage": null}
                """;

        AiChatProviderException exception = assertThrows(
                AiChatProviderException.class,
                () -> provider.generate(context(), "What does は do?", null)
        );

        assertEquals("AI_PROVIDER_EMPTY_RESPONSE", exception.getMessage());
    }

    @Test
    void generate_WhenProviderIsNotConfigured_DoesNotSendARequest() {
        properties.setApiKey("");

        AiChatProviderException exception = assertThrows(
                AiChatProviderException.class,
                () -> provider.generate(context(), "What does は do?", null)
        );

        assertEquals("AI_PROVIDER_NOT_CONFIGURED", exception.getMessage());
        assertEquals(null, requestBody.get());
    }

    private AiChatContext context() {
        return new AiChatContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "N5 Particles",
                "A focused course.",
                "Use basic Japanese particles.",
                "Topic marker",
                "Lesson text:\nUse は for the topic."
        );
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
