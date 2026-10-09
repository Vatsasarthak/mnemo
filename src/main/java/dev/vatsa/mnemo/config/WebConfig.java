package dev.vatsa.mnemo.config;

import dev.vatsa.mnemo.auth.AdminAuthInterceptor;
import dev.vatsa.mnemo.auth.ApiKeyAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Decides which interceptor guards which URL paths. */
@Configuration
class WebConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;

    WebConfig(AdminAuthInterceptor adminAuthInterceptor, ApiKeyAuthInterceptor apiKeyAuthInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.apiKeyAuthInterceptor = apiKeyAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/admin/**");
        registry.addInterceptor(apiKeyAuthInterceptor).addPathPatterns("/v1/**");
    }
}
