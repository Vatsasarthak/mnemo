package dev.vatsa.mnemo.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /admin/keys}. Limits are optional and default to the values below. */
public record CreateKeyRequest(
        @NotBlank @Size(max = 100) String name,
        @Positive Integer rpmLimit,
        @Positive Long dailyTokenLimit) {

    static final int DEFAULT_RPM_LIMIT = 60;
    static final long DEFAULT_DAILY_TOKEN_LIMIT = 100_000;

    // Compact constructor: runs on every construction, so missing limits get defaults here.
    public CreateKeyRequest {
        if (rpmLimit == null) {
            rpmLimit = DEFAULT_RPM_LIMIT;
        }
        if (dailyTokenLimit == null) {
            dailyTokenLimit = DEFAULT_DAILY_TOKEN_LIMIT;
        }
    }
}
