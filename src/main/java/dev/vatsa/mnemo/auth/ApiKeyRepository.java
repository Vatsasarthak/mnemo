package dev.vatsa.mnemo.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain-SQL access to {@code api_keys}. {@link JdbcClient} is Spring's fluent JDBC API:
 * you write the SQL, bind named parameters, and map rows yourself. No ORM, no hidden queries.
 */
@Repository
public class ApiKeyRepository {

    private static final String COLUMNS = "id, name, rpm_limit, daily_token_limit, created_at, revoked_at";

    private final JdbcClient jdbc;

    public ApiKeyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public ApiKey insert(String name, String keyHash, int rpmLimit, long dailyTokenLimit) {
        return jdbc.sql("""
                        INSERT INTO api_keys (name, key_hash, rpm_limit, daily_token_limit)
                        VALUES (:name, :keyHash, :rpmLimit, :dailyTokenLimit)
                        RETURNING %s
                        """.formatted(COLUMNS))
                .param("name", name)
                .param("keyHash", keyHash)
                .param("rpmLimit", rpmLimit)
                .param("dailyTokenLimit", dailyTokenLimit)
                .query(ApiKeyRepository::mapRow)
                .single();
    }

    /** Finds a key by its hash, ignoring revoked keys. Uses the UNIQUE index on key_hash. */
    public Optional<ApiKey> findActiveByHash(String keyHash) {
        return jdbc.sql("SELECT %s FROM api_keys WHERE key_hash = :keyHash AND revoked_at IS NULL".formatted(COLUMNS))
                .param("keyHash", keyHash)
                .query(ApiKeyRepository::mapRow)
                .optional();
    }

    private static ApiKey mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ApiKey(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getInt("rpm_limit"),
                rs.getLong("daily_token_limit"),
                toInstant(rs.getObject("created_at", OffsetDateTime.class)),
                toInstant(rs.getObject("revoked_at", OffsetDateTime.class)));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
