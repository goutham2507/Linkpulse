-- WHY: raw facts, one row per click; event_id UNIQUE lets duplicates be detected later
CREATE TABLE click_events (
    id          BIGSERIAL    PRIMARY KEY,
    event_id    UUID         NOT NULL UNIQUE,
    code        VARCHAR(20)  NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL,
    device      VARCHAR(20)  NOT NULL,
    referrer    VARCHAR(255),
    received_at TIMESTAMPTZ  NOT NULL
);

-- WHY: per-link time-range queries filter on code then occurred_at
CREATE INDEX idx_click_events_code_occurred_at ON click_events (code, occurred_at);

-- WHY: pre-aggregated daily totals so stats reads don't scan raw clicks
CREATE TABLE daily_link_stats (
    code   VARCHAR(20) NOT NULL,
    day    DATE        NOT NULL,
    clicks BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (code, day)
);
