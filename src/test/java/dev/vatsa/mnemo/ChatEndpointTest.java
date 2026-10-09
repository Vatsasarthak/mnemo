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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

    // --- rate limiting and token usage ---

    @Test
    void successfulRequestsRecordGeminisActualTokenUsage() throws Exception {
        String key = createKey();
        gemini.stubFor(WireMock.post(urlEqualTo(GENERATE_PATH)).willReturn(okJson(GEMINI_OK)));

        mvc.perform(chat(key, CHAT_BODY)).andExpect(status().isOk());
        mvc.perform(chat(key, CHAT_BODY)).andExpect(status().isOk());

        // Each answer used 12 input + 13 output (8 answer + 5 thinking) tokens.
        assertThat(tokensUsedToday(key)).isEqualTo(50);
    }

    @Test
    void requestsOverTheRpmLimitGet429WithRetryAfter() throws Exception {
        String key = createKey(2, 100_000);
        gemini.stubFor(WireMock.post(urlEqualTo(GENERATE_PATH)).willReturn(okJson(GEMINI_OK)));
        awaitRoomInCurrentMinute(5_000);

        mvc.perform(chat(key, CHAT_BODY)).andExpect(status().isOk());
        mvc.perform(chat(key, CHAT_BODY)).andExpect(status().isOk());
        String retryAfter = mvc.perform(chat(key, CHAT_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("Requests-per-minute limit reached"))
                .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);

        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);
        gemini.verify(2, postRequestedFor(urlEqualTo(GENERATE_PATH)));
    }

    @Test
    void concurrentRequestsNeverExceedTheRpmLimit() throws Exception {
        int limit = 60;
        int requests = 100;
        String key = createKey(limit, 10_000_000);
        gemini.stubFor(WireMock.post(urlEqualTo(GENERATE_PATH)).willReturn(okJson(GEMINI_OK)));
        awaitRoomInCurrentMinute(15_000);

        // All threads wait on the latch, then fire together to maximise contention on one window row.
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
            for (int i = 0; i < requests; i++) {
                statuses.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(chat(key, CHAT_BODY)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
        }

        Map<Integer, Long> byStatus = new HashMap<>();
        for (Future<Integer> status : statuses) {
            byStatus.merge(status.get(), 1L, Long::sum);
        }
        assertThat(byStatus).containsOnlyKeys(200, 429);
        assertThat(byStatus.get(200)).isEqualTo(limit);
        assertThat(byStatus.get(429)).isEqualTo(requests - limit);
        gemini.verify(limit, postRequestedFor(urlEqualTo(GENERATE_PATH)));
    }

    @Test
    void requestsAfterTheDailyTokenLimitGet429() throws Exception {
        String key = createKey(60, 20);
        gemini.stubFor(WireMock.post(urlEqualTo(GENERATE_PATH)).willReturn(okJson(GEMINI_OK)));

        // 0 used < 20, so this is allowed; it costs 25 tokens. The limit is soft: a request's cost
        // is only known after Gemini answers, so the last allowed request can overshoot.
        mvc.perform(chat(key, CHAT_BODY)).andExpect(status().isOk());
        assertThat(tokensUsedToday(key)).isEqualTo(25);

        String retryAfter = mvc.perform(chat(key, CHAT_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Daily token limit reached"))
                .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);

        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 86_400L);
        gemini.verify(1, postRequestedFor(urlEqualTo(GENERATE_PATH)));
    }

    @Test
    void failedGeminiCallUsesAnRpmSlotButRecordsNoTokens() throws Exception {
        String key = createKey(1, 100_000);
        gemini.stubFor(WireMock.post(urlEqualTo(GENERATE_PATH)).willReturn(serverError()));
        awaitRoomInCurrentMinute(5_000);

        mvc.perform(chat(key, CHAT_BODY)).andExpect(status().isBadGateway());
        mvc.perform(chat(key, CHAT_BODY)).andExpect(status().isTooManyRequests());

        assertThat(tokensUsedToday(key)).isZero();
        gemini.verify(1, postRequestedFor(urlEqualTo(GENERATE_PATH)));
    }

    // --- helpers ---

    private String createKey() throws Exception {
        return createKeyWithBody("{\"name\": \"test-app\"}");
    }

    private String createKey(int rpmLimit, long dailyTokenLimit) throws Exception {
        return createKeyWithBody("{\"name\": \"test-app\", \"rpmLimit\": %d, \"dailyTokenLimit\": %d}"
                .formatted(rpmLimit, dailyTokenLimit));
    }

    private String createKeyWithBody(String json) throws Exception {
        String body = mvc.perform(post("/admin/keys")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.key");
    }

    private long tokensUsedToday(String key) throws Exception {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(u.tokens_used), 0)
                        FROM api_key_daily_usage u JOIN api_keys k ON k.id = u.api_key_id
                        WHERE k.key_hash = :hash
                        """)
                .param("hash", sha256Hex(key))
                .query(Long.class)
                .single();
    }

    /**
     * RPM windows are fixed UTC minutes. If the test could straddle a minute boundary, the window
     * would reset mid-test and let extra requests through, so wait for the next minute instead.
     */
    private static void awaitRoomInCurrentMinute(long neededMillis) throws InterruptedException {
        long intoMinute = System.currentTimeMillis() % 60_000;
        if (intoMinute > 60_000 - neededMillis) {
            Thread.sleep(60_000 - intoMinute + 100);
        }
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
