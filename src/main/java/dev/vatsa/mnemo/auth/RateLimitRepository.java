
package dev.vatsa.mnemo.auth;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RateLimitRepository {

    private final JdbcClient jdbc;

    public RateLimitRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Allows at most rpmLimit requests per UTC minute.
     * Uses an atomic PostgreSQL upsert for concurrent requests.
     */
    public boolean tryConsumeRequest(long apiKeyId, int rpmLimit) {
        Instant now = Instant.now();
        Instant windowStart = Instant.ofEpochSecond(
                (now.getEpochSecond() / 60) * 60);

        return jdbc.sql("""
                INSERT INTO api_key_rate_windows
                    (api_key_id, window_start, request_count)
                SELECT :apiKeyId, :windowStart, 1
                WHERE :rpmLimit > 0
                ON CONFLICT (api_key_id, window_start)
                DO UPDATE SET request_count =
                    api_key_rate_windows.request_count + 1
                WHERE api_key_rate_windows.request_count < :rpmLimit
                RETURNING request_count
                """)
                .param("apiKeyId", apiKeyId)
                // The Postgres driver cannot bind Instant; OffsetDateTime maps to TIMESTAMPTZ.
                .param("windowStart", OffsetDateTime.ofInstant(windowStart, ZoneOffset.UTC))
                .param("rpmLimit", rpmLimit)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    /**
     * Returns token usage for the current UTC day.
     */
    public long getDailyTokensUsed(long apiKeyId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        return jdbc.sql("""
                SELECT COALESCE(
                    (SELECT tokens_used
                     FROM api_key_daily_usage
                     WHERE api_key_id = :apiKeyId
                       AND usage_date = :usageDate),
                    0
                )
                """)
                .param("apiKeyId", apiKeyId)
                .param("usageDate", today)
                .query(Long.class)
                .single();
    }

    /**
     * Adds actual input and output tokens returned by Gemini.
     */
    public void recordTokenUsage(long apiKeyId, long tokens) {
        if (tokens <= 0) {
            return;
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        jdbc.sql("""
                INSERT INTO api_key_daily_usage
                    (api_key_id, usage_date, tokens_used)
                VALUES (:apiKeyId, :usageDate, :tokens)
                ON CONFLICT (api_key_id, usage_date)
                DO UPDATE SET tokens_used =
                    api_key_daily_usage.tokens_used + EXCLUDED.tokens_used
                """)
                .param("apiKeyId", apiKeyId)
                .param("usageDate", today)
                .param("tokens", tokens)
                .update();
    }
}
