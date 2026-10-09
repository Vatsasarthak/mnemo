package dev.vatsa.mnemo.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Plain unit tests: no Spring, no database. They pass once the TODO(you) methods are implemented. */
class ApiKeyServiceTest {

    private final ApiKeyService service = new ApiKeyService(null);

    @Test
    void sha256HexMatchesKnownVector() {
        // Standard SHA-256 test vector for "abc".
        assertThat(ApiKeyService.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256HexIsDeterministicLowercaseHex() {
        String key = service.generateRawKey();
        String hash = ApiKeyService.sha256Hex(key);

        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(ApiKeyService.sha256Hex(key)).isEqualTo(hash);
        assertThat(hash).isNotEqualTo(key);
    }

    @Test
    void generatedKeysArePrefixedUrlSafeAndFullLength() {
        String key = service.generateRawKey();

        // 32 bytes -> 43 base64 characters without padding.
        assertThat(key).startsWith(ApiKeyService.KEY_PREFIX);
        assertThat(key.substring(ApiKeyService.KEY_PREFIX.length())).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void generatedKeysAreUnique() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            keys.add(service.generateRawKey());
        }
        assertThat(keys).hasSize(1_000);
    }
}
