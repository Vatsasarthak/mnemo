package dev.vatsa.mnemo.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the one {@link RestClient} used to call Gemini. The API key goes in the
 * {@code x-goog-api-key} header (not the URL) so it never ends up in access logs.
 */
@Configuration
class GeminiClientConfig {

    @Bean
    RestClient geminiRestClient(RestClient.Builder builder, MnemoProperties properties) {
        MnemoProperties.Gemini gemini = properties.gemini();

        // The JDK's built-in HttpClient; the read timeout bounds how long we wait for Gemini's answer.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(gemini.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(gemini.readTimeout());

        return builder
                .baseUrl(gemini.baseUrl())
                .defaultHeader("x-goog-api-key", gemini.apiKey())
                .requestFactory(requestFactory)
                .build();
    }
}
