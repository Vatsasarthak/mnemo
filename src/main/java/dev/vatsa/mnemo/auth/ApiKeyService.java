
package dev.vatsa.mnemo.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class ApiKeyService {

    static final String KEY_PREFIX = "mnemo_";
    static final int KEY_RANDOM_BYTES = 32;

    private final ApiKeyRepository repository;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    public CreatedKey create(String name, int rpmLimit, long dailyTokenLimit) {
        String rawKey = generateRawKey();
        String keyHash = sha256Hex(rawKey);

        ApiKey savedKey = repository.insert(
                name,
                keyHash,
                rpmLimit,
                dailyTokenLimit
        );

        return new CreatedKey(savedKey, rawKey);
    }

    public Optional<ApiKey> findActive(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }

        String keyHash = sha256Hex(rawKey);
        return repository.findActiveByHash(keyHash);
    }

    String generateRawKey() {
        byte[] bytes = new byte[KEY_RANDOM_BYTES];
        random.nextBytes(bytes);

        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        return KEY_PREFIX + encoded;
    }

    static String sha256Hex(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    rawKey.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", e
            );
        }
    }

    public record CreatedKey(ApiKey apiKey, String rawKey) {
    }
}