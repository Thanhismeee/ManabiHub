package com.manabihub.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manabihub.ai.config.AiChatProviderProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiWritingAssistanceProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorizationHeader = new AtomicReference<>();

    private AiChatProviderProperties properties;
    private HttpServer server;
    private String responseBody;
    private AiWritingAssistanceProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        responseBody = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\"grammarSuggestions\\":[{\\"error\\":\\"go\\",\\"correction\\":\\"went\\",\\"explanation\\":\\"Past tense\\"}],\\"vocabularySuggestions\\":[],\\"structureSuggestions\\":[],\\"revisionGuidance\\":\\"Add one supporting detail.\\"}"
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 55,
                    "completion_tokens": 21
                  }
                }
                """;

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::handleRequest);
        server.start();

        properties = new AiChatProviderProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setEndpoint("/v1/chat/completions");
        properties.setApiKey("provider-secret");
        properties.setModel("provider-model");
        properties.setTimeoutSeconds(3);

        provider = new AiWritingAssistanceProvider(
                RestClient.builder(),
                properties,
                objectMapper
        );
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void generate_RequestsJsonSuggestionsAndParsesNonGradingSchema() throws Exception {
        AiWritingAssistanceProvider.Result result = provider.generate(
                "Write about your weekend.",
                "Use N5 grammar.",
                "I go to the library last weekend."
        );

        assertTrue(result.grammarSuggestions().isArray());
        assertFalse(result.grammarSuggestions().isEmpty());
        assertTrue(result.vocabularySuggestions().isArray());
        assertTrue(result.structureSuggestions().isArray());
        assertEquals("Add one supporting detail.", result.revisionGuidance());
        assertEquals("openai-compatible", result.provider());
        assertEquals(55, result.inputTokens());
        assertEquals(21, result.outputTokens());
        assertEquals("Bearer provider-secret", authorizationHeader.get());

        JsonNode request = objectMapper.readTree(requestBody.get());
        assertEquals("provider-model", request.path("model").asText());
        assertEquals("json_object", request.path("response_format").path("type").asText());
        assertTrue(request.path("messages").get(0).path("content").asText()
                .contains("Do not output a final score"));
        assertTrue(request.path("messages").get(0).path("content").asText()
                .contains("Write about your weekend."));
        assertEquals("I go to the library last weekend.",
                request.path("messages").get(1).path("content").asText());
    }

    @Test
    void generate_WhenStructuredOutputIsMissingRequiredArrays_FailsClosed() {
        responseBody = """
                {
                  "choices": [
                    {"message": {"content": "{\\"revisionGuidance\\":\\"Incomplete schema\\"}"}}
                  ]
                }
                """;

        AiChatProviderException exception = assertThrows(
                AiChatProviderException.class,
                () -> provider.generate("Prompt", "Rubric", "Student content")
        );

        assertEquals("AI_PROVIDER_INVALID_SCHEMA", exception.getMessage());
    }

    @Test
    void generate_WhenProviderIsNotConfigured_DoesNotSendARequest() {
        properties.setBaseUrl("");

        AiChatProviderException exception = assertThrows(
                AiChatProviderException.class,
                () -> provider.generate("Prompt", "Rubric", "Student content")
        );

        assertEquals("AI_PROVIDER_NOT_CONFIGURED", exception.getMessage());
        assertEquals(null, requestBody.get());
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
