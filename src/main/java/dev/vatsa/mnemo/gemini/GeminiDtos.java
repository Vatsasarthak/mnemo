package dev.vatsa.mnemo.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Records that mirror the JSON of Gemini's {@code generateContent} REST API.
 * Only the fields we use are listed; Jackson ignores the rest of the response.
 * {@code NON_NULL} leaves unset fields out of the request instead of sending {@code null}.
 */
final class GeminiDtos {

    private GeminiDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GenerateContentRequest(Content systemInstruction, List<Content> contents, GenerationConfig generationConfig) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Content(String role, List<Part> parts) {
    }

    /** {@code thought} is true for the model's thinking summaries, which are not part of the answer. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Part(String text, Boolean thought) {

        static Part text(String text) {
            return new Part(text, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GenerationConfig(Double temperature) {
    }

    record GenerateContentResponse(List<Candidate> candidates, UsageMetadata usageMetadata) {
    }

    record Candidate(Content content, String finishReason) {
    }

    /** Thinking models bill their thinking tokens as output, so outputTokens adds both counts. */
    record UsageMetadata(Integer promptTokenCount, Integer candidatesTokenCount, Integer thoughtsTokenCount) {

        int inputTokens() {
            return promptTokenCount == null ? 0 : promptTokenCount;
        }

        int outputTokens() {
            return (candidatesTokenCount == null ? 0 : candidatesTokenCount)
                    + (thoughtsTokenCount == null ? 0 : thoughtsTokenCount);
        }
    }
}
