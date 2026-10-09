package dev.vatsa.mnemo;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-application test: real Postgres (Testcontainers) and a fake Gemini (WireMock).
 * MockMvc sends requests through the whole Spring MVC stack (interceptors, validation,
 * exception handler) without opening a network port.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChatEndpointTest {

    private static final String ADMIN_TOKEN = "test-admin-token-0123456789abcdef-0123";
    private static final String GEMINI_KEY = "test-gemini-key";
    private static final String GENERATE_PATH = "/v1beta/models/gemini-3.8-flash:generateContent";

    private static final String CHAT_BODY = """
            {
              "system": "You are a support assistant for a laundry app.",
              "messages": [
                { "role": "user", "content": "Hi" },
                { "role": "assistant", "content": "Hello! How can I help?" },
                { "role": "user", "content": "How long does dry cleaning take?" }
              ],
              "temperature": 0.2
            }
            """;

    private static final String GEMINI_OK = """
            {
              "candidates": [{
                "content": { "role": "model", "parts": [
                  { "text": "Dry cleaning " },
                  { "text": "takes 2 to 3 days." }
                ]},
                "finishReason": "STOP"
              }],
              "usageMetadata": { "promptTokenCount": 12, "candidatesTokenCount": 8,
                                 "thoughtsTokenCount": 5, "totalTokenCount": 25 },
              "modelVersion": "gemini-3.8-flash"
            }
            """;

    // A WireMock server on a random free port, started before the Spring context and reset after each test.
    @RegisterExtension
    static WireMockExtension gemini = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    // Runs before the Spring context starts; these values win over application.yml and .env.
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("mnemo.gemini.base-url", gemini::baseUrl);
        registry.add("mnemo.gemini.api-key", () -> GEMINI_KEY);
        registry.add("mnemo.gemini.read-timeout", () -> "1s");
        registry.add("mnemo.admin-token", () -> ADMIN_TOKEN);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    // --- admin keys ---

    @Test
    void adminCreatesKeyAndOnlyItsHashIsStored() throws Exception {
        String body = mvc.perform(post("/admin/keys")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"laundry-app\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.name").value("laundry-app"))
                .andExpect(jsonPath("$.rpmLimit").value(60))
                .andExpect(jsonPath("$.dailyTokenLimit").value(100_000))
                .andReturn().getResponse().getContentAsString();

        String rawKey = JsonPath.read(body, "$.key");
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        String storedHash = jdbc.sql("SELECT key_hash FROM api_keys WHERE id = :id")
                .param("id", id).query(String.class).single();
        assertThat(rawKey).startsWith("mnemo_");
        assertThat(storedHash).isEqualTo(sha256Hex(rawKey)).isNotEqualTo(rawKey);
    }

    @Test
    void adminEndpointRejectsWrongOrMissingToken() throws Exception {
        mvc.perform(post("/admin/keys")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-the-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"x\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/admin/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    // --- chat happy path ---

    @Test
    void validKeyGetsGeminiAnswer() throws Exception {
        String key = createKey();
        gemini.stubFor(WireMock.post(urlEqualTo(GENERATE_PATH)).willReturn(okJson(GEMINI_OK)));

        mvc.perform(chat(key, CHAT_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Model-Used", "gemini-3.8-flash"))
                .andExpect(jsonPath("$.content").value("Dry cleaning takes 2 to 3 days."))
                .andExpect(jsonPath("$.model").value("gemini-3.8-flash"))
                .andExpect(jsonPath("$.usage.inputTokens").value(12))
                .andExpect(jsonPath("$.usage.outputTokens").value(13))
                .andExpect(jsonPath("$.latencyMs").isNumber());

        // urlEqualTo matches the full URL, so this also proves the API key is not in a query string.
        gemini.verify(1, postRequestedFor(urlEqualTo(GENERATE_PATH))
                .withHeader("x-goog-api-key", equalTo(GEMINI_KEY))
                .withRequestBody(matchingJsonPath("$.systemInstruction.parts[0].text",
                        equalTo("You are a support assistant for a laundry app.")))
                .withRequestBody(matchingJsonPath("$.contents[0].role", equalTo("user")))
                .withRequestBody(matchingJsonPath("$.contents[1].role", equalTo("model")))
                .withRequestBody(matchingJsonPath("$.contents[2].parts[0].text",
                        equalTo("How long does dry cleaning take?")))
                .withRequestBody(matchingJsonPath("$.generationConfig.temperature", equalTo("0.2"))));
    }

    // --- chat auth failures: none of them may reach Gemini ---

    @Test
    void unknownKeyIsRejected() throws Exception {
        mvc.perform(chat("mnemo_this-key-does-not-exist", CHAT_BODY))
                .andExpect(status().isUnauthorized());
        gemini.verify(0, postRequestedFor(urlEqualTo(GENERATE_PATH)));
    }

    @Test
    void missingOrMalformedAuthorizationIsRejected() throws Exception {
        mvc.perform(post("/v1/chat").contentType(MediaType.APPLICATION_JSON).content(CHAT_BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_BODY))
                .andExpect(status().isUnauthorized());
        gemini.verify(0, postRequestedFor(urlEqualTo(GENERATE_PATH)));
    }

    @Test
    void revokedKeyIsRejected() throws Exception {
        String key = createKey();
        jdbc.sql("UPDATE api_keys SET revoked_at = now() WHERE key_hash = :hash")
                .param("hash", sha256Hex(key)).update();

        mvc.perform(chat(key, CHAT_BODY)).andExpect(status().isUnauthorized());
        gemini.verify(0, postRequestedFor(urlEqualTo(GENERATE_PATH)));
    }

    // --- request validation ---

    @Test
    void invalidRequestsGet400() throws Exception {
        String key = createKey();
        mvc.perform(chat(key, "{\"messages\": []}")).andExpect(status().isBadRequest());
        mvc.perform(chat(key, "{\"messages\": [{\"role\": \"system\", \"content\": \"hi\"}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(chat(key, "{\"messages\": [{\"role\": \"user\", \"content\": \"hi\"}], \"temperature\": 3}"))
                .andExpect(status().isBadRequest());
        mvc.perform(chat(key, "{\"messages\": [{\"role\": \"user\", \"content\": \"hi\"}], \"stream\": true}"))
                .andExpect(status().isBadRequest());
        gemini.verify(0, postRequestedFor(urlEqualTo(GENERATE_PATH)));
    }

    // --- upstream failures ---

    @Test
    void geminiServerErrorBecomes502() throws Exception {
        String key = createKey();
        gemini.stubFor(WireMock.post(urlEqualTo(GENERATE_PATH)).willReturn(serverError()));

        mvc.perform(chat(key, CHAT_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    @Test
    void geminiTimeoutBecomes504() throws Exception {
        String key = createKey();
        // Read timeout is 1s in this test; Gemini "answers" after 3s.
        gemini.stubFor(WireMock.post(urlEqualTo(GENERATE_PATH))
                .willReturn(okJson(GEMINI_OK).withFixedDelay(3_000)));

        mvc.perform(chat(key, CHAT_BODY))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504));
    }

    @Test
    void geminiWithNoCandidatesBecomes502() throws Exception {
        String key = createKey();
        gemini.stubFor(WireMock.post(urlEqualTo(GENERATE_PATH))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"promptFeedback\": {\"blockReason\": \"SAFETY\"}}")));

        mvc.perform(chat(key, CHAT_BODY)).andExpect(status().isBadGateway());
    }

    // --- helpers ---

    private String createKey() throws Exception {
        String body = mvc.perform(post("/admin/keys")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"test-app\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.key");
    }

    private static org.springframework.test.web.servlet.RequestBuilder chat(String key, String json) {
        return post("/v1/chat")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    /** Independent of ApiKeyService on purpose: the test checks the stored value, not the code that made it. */
    private static String sha256Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
