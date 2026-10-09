package dev.vatsa.mnemo.auth;

import java.security.SecureRandom;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Creates and looks up gateway API keys. Raw keys are never stored: only their SHA-256 hash
 * goes to Postgres, so a leaked database leaks no usable keys.
 */
@Service
public class ApiKeyService {

    static final String KEY_PREFIX = "mnemo_";
    static final int KEY_RANDOM_BYTES = 32;

    private final ApiKeyRepository repository;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    /**
     * Generates a new raw key, stores only its hash, and returns both the saved row and the raw key.
     * The caller shows the raw key to the admin exactly once.
     */
    public CreatedKey create(String name, int rpmLimit, long dailyTokenLimit) {
        // TODO(you): generate a raw key, hash it, insert via repository.insert(...), return new CreatedKey(row, rawKey)
        throw new UnsupportedOperationException("TODO(you): implement ApiKeyService.create");
    }

    /** Returns the active (not revoked) key for this raw key, or empty if it is unknown or revoked. */
    public Optional<ApiKey> findActive(String rawKey) {
        // TODO(you): hash the raw key and look it up with repository.findActiveByHash(...)
        throw new UnsupportedOperationException("TODO(you): implement ApiKeyService.findActive");
    }

    /**
     * Returns {@link #KEY_PREFIX} followed by {@link #KEY_RANDOM_BYTES} bytes from {@link #random},
     * encoded as URL-safe base64 without padding (so the key is safe in headers and URLs).
     */
    String generateRawKey() {
        // TODO(you): hint: java.util.Base64.getUrlEncoder().withoutPadding()
        throw new UnsupportedOperationException("TODO(you): implement ApiKeyService.generateRawKey");
    }

    /** Returns the SHA-256 of the raw key's UTF-8 bytes as 64 lowercase hex characters. */
    static String sha256Hex(String rawKey) {
        // TODO(you): hint: java.security.MessageDigest and java.util.HexFormat
        throw new UnsupportedOperationException("TODO(you): implement ApiKeyService.sha256Hex");
    }

    /** A freshly created key: the stored row plus the raw key, which exists only in this response. */
    public record CreatedKey(ApiKey apiKey, String rawKey) {
    }
}
