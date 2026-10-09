package dev.vatsa.mnemo.admin;

import dev.vatsa.mnemo.auth.ApiKeyService.CreatedKey;
import java.time.Instant;

/** Response of {@code POST /admin/keys}. {@code key} is the raw key, returned this one time only. */
public record CreateKeyResponse(
        long id,
        String name,
        String key,
        int rpmLimit,
        long dailyTokenLimit,
        Instant createdAt) {

    static CreateKeyResponse from(CreatedKey created) {
        var row = created.apiKey();
        return new CreateKeyResponse(
                row.id(), row.name(), created.rawKey(), row.rpmLimit(), row.dailyTokenLimit(), row.createdAt());
    }
}
