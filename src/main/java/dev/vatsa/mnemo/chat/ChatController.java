package dev.vatsa.mnemo.chat;

import dev.vatsa.mnemo.auth.ApiKey;
import dev.vatsa.mnemo.auth.ApiKeyAuthInterceptor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {

    private final ChatService chatService;

    ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** {@code @RequestAttribute} reads the ApiKey that ApiKeyAuthInterceptor stored on the request. */
    @PostMapping("/v1/chat")
    ResponseEntity<ChatResponse> chat(
            @RequestAttribute(ApiKeyAuthInterceptor.API_KEY_ATTRIBUTE) ApiKey apiKey,
            @Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatService.chat(apiKey, request);
        return ResponseEntity.ok()
                .header("X-Model-Used", response.model())
                .body(response);
    }
}
