package dev.vatsa.mnemo.chat;

import dev.vatsa.mnemo.auth.ApiKey;
import dev.vatsa.mnemo.auth.RateLimitRepository;
import dev.vatsa.mnemo.config.MnemoProperties;
import dev.vatsa.mnemo.error.RateLimitExceededException;
import dev.vatsa.mnemo.gemini.GeminiClient;
import dev.vatsa.mnemo.gemini.GeminiResult;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The request pipeline behind {@code /v1/chat}. Auth already ran in the interceptor.
 * Order: reject bad requests, enforce limits, call Gemini, record token usage.
 *
 * <p>Deliberately not {@code @Transactional}: each RateLimitRepository statement is atomic on
 * its own, and a transaction spanning the Gemini call would hold a pooled DB connection
 * (and row locks) for up to the read timeout.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final GeminiClient geminiClient;
    private final RateLimitRepository rateLimits;
    private final String primaryModel;

    public ChatService(GeminiClient geminiClient, RateLimitRepository rateLimits, MnemoProperties properties) {
        this.geminiClient = geminiClient;
        this.rateLimits = rateLimits;
        this.primaryModel = properties.gemini().primaryModel();
    }

    public ChatResponse chat(ApiKey apiKey, ChatRequest request) {
        if (request.streaming()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Streaming is not supported yet");
        }
        enforceLimits(apiKey);

        long start = System.nanoTime();
        GeminiResult result = geminiClient.generate(primaryModel, request);
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        recordTokenUsage(apiKey, result);
        return new ChatResponse(
                result.text(),
                result.model(),
                new ChatResponse.Usage(result.inputTokens(), result.outputTokens()),
                latencyMs);
    }

    /**
     * The daily check runs first and only reads, so a request rejected for tokens does not
     * use up an RPM slot. The daily limit is soft: the check and the later record are separate
     * statements and a call's cost is unknown until Gemini answers, so concurrent in-flight
     * requests can push usage past the limit. The RPM limit is exact (one atomic upsert).
     */
    private void enforceLimits(ApiKey apiKey) {
        if (rateLimits.getDailyTokensUsed(apiKey.id()) >= apiKey.dailyTokenLimit()) {
            throw new RateLimitExceededException("Daily token limit reached", secondsUntilNextUtcDay());
        }
        if (!rateLimits.tryConsumeRequest(apiKey.id(), apiKey.rpmLimit())) {
            throw new RateLimitExceededException("Requests-per-minute limit reached", secondsUntilNextMinute());
        }
    }

    /** Gemini already answered (and was paid for), so a failed write is logged rather than failing the request. */
    private void recordTokenUsage(ApiKey apiKey, GeminiResult result) {
        long tokens = (long) result.inputTokens() + result.outputTokens();
        try {
            rateLimits.recordTokenUsage(apiKey.id(), tokens);
        } catch (DataAccessException e) {
            log.error("Could not record {} tokens for api key {}; daily usage is under-counted", tokens, apiKey.id(), e);
        }
    }

    // Both match RateLimitRepository's windows: fixed UTC minutes and UTC calendar days.
    private static long secondsUntilNextMinute() {
        return 60 - (Instant.now().getEpochSecond() % 60);
    }

    private static long secondsUntilNextUtcDay() {
        Instant now = Instant.now();
        Instant midnight = LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long millis = Duration.between(now, midnight).toMillis();
        return Math.max(1, (millis + 999) / 1000); // round up so clients never retry before the reset
    }
}
