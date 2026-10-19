CREATE TABLE links (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(20)   NOT NULL UNIQUE,
    long_url   VARCHAR(2048) NOT NULL,
    user_id    BIGINT        NOT NULL,
    expires_at TIMESTAMP,
    is_active  BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_links_user_id ON links(user_id);