package dev.vatsa.mnemo.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code POST /v1/chat}. The Bean Validation annotations are checked when the
 * controller parameter is marked {@code @Valid}; a violation becomes a 400 before any code runs.
 */
public record ChatRequest(
        @Size(max = 20_000) String system,
        @NotEmpty @Size(max = 100) List<@Valid Message> messages,
        @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        Boolean stream,
        Boolean cache) {

    public record Message(
            @NotBlank @Pattern(regexp = "user|assistant", message = "must be 'user' or 'assistant'") String role,
            @NotBlank @Size(max = 50_000) String content) {
    }

    boolean streaming() {
        return Boolean.TRUE.equals(stream);
    }
}
