-- API keys for client apps. Only the SHA-256 hash of each key is stored;
-- the raw key is shown once at creation and never saved.
CREATE TABLE api_keys (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name              TEXT        NOT NULL,
    key_hash          CHAR(64)    NOT NULL UNIQUE,  -- hex SHA-256; UNIQUE also gives us the lookup index
    rpm_limit         INTEGER     NOT NULL DEFAULT 60,
    daily_token_limit BIGINT      NOT NULL DEFAULT 100000,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at        TIMESTAMPTZ
);
