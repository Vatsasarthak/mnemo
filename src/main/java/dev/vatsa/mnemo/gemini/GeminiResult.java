package dev.vatsa.mnemo.gemini;

/** What the rest of the gateway needs from one Gemini answer, free of Gemini's JSON shape. */
public record GeminiResult(String text, String model, int inputTokens, int outputTokens) {
}
