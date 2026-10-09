package dev.vatsa.mnemo.auth;

import dev.vatsa.mnemo.config.MnemoProperties;
import dev.vatsa.mnemo.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards {@code /admin/**}: the request must send {@code Authorization: Bearer <ADMIN_TOKEN>}.
 * A {@link HandlerInterceptor} runs before the controller; throwing here skips the controller
 * and lets {@code ApiExceptionHandler} turn the exception into a 401.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] adminToken;

    public AdminAuthInterceptor(MnemoProperties properties) {
        this.adminToken = properties.adminToken().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("Missing admin bearer token");
        }
        byte[] presented = header.substring(BEARER_PREFIX.length()).strip().getBytes(StandardCharsets.UTF_8);
        // Constant-time compare: equals() returns early at the first wrong byte, which leaks
        // how much of the token was right through response timing.
        if (!MessageDigest.isEqual(adminToken, presented)) {
            throw new UnauthorizedException("Invalid admin token");
        }
        return true;
    }
}
