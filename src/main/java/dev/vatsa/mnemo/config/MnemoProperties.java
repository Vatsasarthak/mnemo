package dev.vatsa.mnemo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * All {@code mnemo.*} settings from application.yml, bound into one typed object.
 * {@code @Validated} makes startup fail fast if a required value (like the Gemini key) is missing.
 */
@Validated
@ConfigurationProperties("mnemo")
public record MnemoProperties(
        @NotBlank(message = "set ADMIN_TOKEN in .env")
        @Size(min = 32, message = "ADMIN_TOKEN must be at least 32 characters")
        String adminToken,
        @Valid @NotNull Gemini gemini) {

    public record Gemini(
            @NotBlank String baseUrl,
            @NotBlank(message = "set GEMINI_API_KEY in .env") String apiKey,
            @NotBlank String primaryModel,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout) {

        // Records print every field in toString(); keep the API key out of logs.
        @Override
        public String toString() {
            return "Gemini[baseUrl=%s, apiKey=***, primaryModel=%s, connectTimeout=%s, readTimeout=%s]"
                    .formatted(baseUrl, primaryModel, connectTimeout, readTimeout);
        }
    }

    @Override
    public String toString() {
        return "MnemoProperties[adminToken=***, gemini=%s]".formatted(gemini);
    }
}
