package dev.vatsa.mnemo.chat;

import dev.vatsa.mnemo.auth.ApiKey;
import dev.vatsa.mnemo.config.MnemoProperties;
import dev.vatsa.mnemo.gemini.GeminiClient;
import dev.vatsa.mnemo.gemini.GeminiResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The request pipeline behind {@code /v1/chat}. Auth already ran in the interceptor.
 * Milestone 1 calls Gemini directly; rate limiting and the caches slot in here in later milestones.
 */
@Service
public class ChatService {

    private final GeminiClient geminiClient;
    private final String primaryModel;

    public ChatService(GeminiClient geminiClient, MnemoProperties properties) {
        this.geminiClient = geminiClient;
        this.primaryModel = properties.gemini().primaryModel();
    }

    /** {@code apiKey} is unused until milestone 2, where it drives rate limits and cache scope. */
    public ChatResponse chat(ApiKey apiKey, ChatRequest request) {
        if (request.streaming()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Streaming is not supported yet");
        }
        long start = System.nanoTime();
        GeminiResult result = geminiClient.generate(primaryModel, request);
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return new ChatResponse(
                result.text(),
                result.model(),
                new ChatResponse.Usage(result.inputTokens(), result.outputTokens()),
                latencyMs);
    }
}
