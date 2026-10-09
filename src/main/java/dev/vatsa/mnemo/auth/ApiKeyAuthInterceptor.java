
package dev.vatsa.mnemo.auth;

import dev.vatsa.mnemo.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    public static final String API_KEY_ATTRIBUTE = "apiKey";

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthInterceptor(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException(
                    "Missing API key bearer token"
            );
        }

        String rawKey = header
                .substring(BEARER_PREFIX.length())
                .strip();

        if (rawKey.isEmpty()) {
            throw new UnauthorizedException(
                    "Empty API key"
            );
        }

        ApiKey apiKey = apiKeyService.findActive(rawKey)
                .orElseThrow(() -> new UnauthorizedException(
                        "Invalid or revoked API key"
                ));

        request.setAttribute(API_KEY_ATTRIBUTE, apiKey);

        return true;
    }
}