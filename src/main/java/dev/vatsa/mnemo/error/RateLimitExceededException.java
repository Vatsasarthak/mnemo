package dev.vatsa.mnemo.error;

/** Thrown when an API key is over its requests-per-minute or daily token limit. Mapped to 429. */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** Seconds until the limit resets; sent to the client as the {@code Retry-After} header. */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
