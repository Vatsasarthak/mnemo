CREATE TABLE api_key_rate_windows (
    api_key_id BIGINT NOT NULL REFERENCES api_keys(id) ON DELETE CASCADE,
    window_start TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0 CHECK (request_count >= 0),
    PRIMARY KEY (api_key_id, window_start)
);

CREATE TABLE api_key_daily_usage (
    api_key_id BIGINT NOT NULL REFERENCES api_keys(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    tokens_used BIGINT NOT NULL DEFAULT 0 CHECK (tokens_used >= 0),
    PRIMARY KEY (api_key_id, usage_date)
);

CREATE INDEX idx_api_key_daily_usage_date
    ON api_key_daily_usage (usage_date);