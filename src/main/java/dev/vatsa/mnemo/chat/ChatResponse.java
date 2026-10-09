package dev.vatsa.mnemo.chat;

/** Body returned by {@code POST /v1/chat}. The {@code cache} block arrives in milestone 2. */
public record ChatResponse(String content, String model, Usage usage, long latencyMs) {

    public record Usage(int inputTokens, int outputTokens) {
    }
}
