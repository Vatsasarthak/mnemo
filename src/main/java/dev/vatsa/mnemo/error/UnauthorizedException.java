package dev.vatsa.mnemo.error;

/** Thrown when a request has a missing, unknown or revoked credential. Mapped to 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
