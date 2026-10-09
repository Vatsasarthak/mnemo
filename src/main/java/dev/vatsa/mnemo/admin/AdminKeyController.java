package dev.vatsa.mnemo.admin;

import dev.vatsa.mnemo.auth.ApiKeyService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only endpoints, guarded by {@code AdminAuthInterceptor}. */
@RestController
@RequestMapping("/admin/keys")
class AdminKeyController {

    private final ApiKeyService apiKeyService;

    AdminKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    ResponseEntity<CreateKeyResponse> create(@Valid @RequestBody CreateKeyRequest request) {
        var created = apiKeyService.create(request.name(), request.rpmLimit(), request.dailyTokenLimit());
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore()) // the body holds a secret; no proxy or browser may cache it
                .body(CreateKeyResponse.from(created));
    }
}
