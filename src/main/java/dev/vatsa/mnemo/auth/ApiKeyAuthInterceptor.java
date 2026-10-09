package dev.vatsa.mnemo.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards {@code /v1/**}: the request must send {@code Authorization: Bearer <gateway key>}
 * for an active key. On success the {@link ApiKey} is stored as a request attribute, so
 * controllers can read it with {@code @RequestAttribute(API_KEY_ATTRIBUTE)}.
 */
@Component
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    public static final String API_KEY_ATTRIBUTE = "mnemo.apiKey";

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthInterceptor(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // TODO(you):
        //  1. Read the Authorization header. Missing, or not "Bearer <token>" -> throw UnauthorizedException.
        //  2. Look the token up with apiKeyService.findActive(...). Empty -> throw UnauthorizedException.
        //  3. request.setAttribute(API_KEY_ATTRIBUTE, apiKey) and return true.
        // AdminAuthInterceptor in this package shows the same pattern for the admin token.
        throw new UnsupportedOperationException("TODO(you): implement ApiKeyAuthInterceptor.preHandle");
    }
}
