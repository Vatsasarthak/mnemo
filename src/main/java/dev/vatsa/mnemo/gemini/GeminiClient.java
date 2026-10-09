package dev.vatsa.mnemo.gemini;

import dev.vatsa.mnemo.chat.ChatRequest;
import dev.vatsa.mnemo.gemini.GeminiDtos.Content;
import dev.vatsa.mnemo.gemini.GeminiDtos.GenerateContentRequest;
import dev.vatsa.mnemo.gemini.GeminiDtos.GenerateContentResponse;
import dev.vatsa.mnemo.gemini.GeminiDtos.GenerationConfig;
import dev.vatsa.mnemo.gemini.GeminiDtos.Part;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Calls Gemini's {@code generateContent} REST endpoint and maps between our DTOs and Gemini's. */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient restClient;

    public GeminiClient(RestClient geminiRestClient) {
        this.restClient = geminiRestClient;
    }

    public GeminiResult generate(String model, ChatRequest request) {
        GenerateContentResponse response;
        try {
            response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .body(toGemini(request))
                    .retrieve()
                    .body(GenerateContentResponse.class);
        } catch (RestClientResponseException e) {
            // Gemini answered with 4xx/5xx. Log its error body for debugging; never return it to clients.
            log.warn("Gemini {} returned {}: {}", model, e.getStatusCode(), e.getResponseBodyAsString());
            throw new GeminiException("Gemini returned " + e.getStatusCode(), false, e);
        } catch (ResourceAccessException e) {
            // I/O failure before any response: connection refused, reset, or our read timeout.
            boolean timeout = isTimeout(e);
            log.warn("Gemini {} call failed ({})", model, timeout ? "timeout" : e.getMessage());
            throw new GeminiException(timeout ? "Gemini timed out" : "Gemini unreachable", timeout, e);
        }
        return toResult(model, response);
    }

    private static GenerateContentRequest toGemini(ChatRequest request) {
        Content system = request.system() == null || request.system().isBlank()
                ? null
                : new Content(null, List.of(Part.text(request.system())));
        // Gemini calls the assistant role "model".
        List<Content> contents = request.messages().stream()
                .map(m -> new Content("assistant".equals(m.role()) ? "model" : "user", List.of(Part.text(m.content()))))
                .toList();
        GenerationConfig config = request.temperature() == null ? null : new GenerationConfig(request.temperature());
        return new GenerateContentRequest(system, contents, config);
    }

    private static GeminiResult toResult(String model, GenerateContentResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            // No candidates usually means the prompt was blocked by Gemini's safety filters.
            throw new GeminiException("Gemini returned no candidates", false, null);
        }
        Content content = response.candidates().getFirst().content();
        String text = content == null || content.parts() == null ? "" : content.parts().stream()
                .filter(p -> p.text() != null && !Boolean.TRUE.equals(p.thought()))
                .map(Part::text)
                .collect(Collectors.joining());
        int inputTokens = response.usageMetadata() == null ? 0 : response.usageMetadata().inputTokens();
        int outputTokens = response.usageMetadata() == null ? 0 : response.usageMetadata().outputTokens();
        return new GeminiResult(text, model, inputTokens, outputTokens);
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }
}
