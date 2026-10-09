package dev.vatsa.mnemo.auth;

import java.time.Instant;

/** One row of {@code api_keys}. Deliberately has no raw key and no hash: neither should travel around the app. */
public record ApiKey(
        long id,
        String name,
        int rpmLimit,
        long dailyTokenLimit,
        Instant createdAt,
        Instant revokedAt) {
}
