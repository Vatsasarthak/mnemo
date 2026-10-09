package dev.vatsa.mnemo.gemini;

/** A failed Gemini call. {@code timeout} maps to 504; everything else maps to 502. */
public class GeminiException extends RuntimeException {

    private final boolean timeout;

    public GeminiException(String message, boolean timeout, Throwable cause) {
        super(message, cause);
        this.timeout = timeout;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
