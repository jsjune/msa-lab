CREATE TABLE IF NOT EXISTS gateway_log (
    id            BIGSERIAL    PRIMARY KEY,
    tx_id         VARCHAR(64)  NOT NULL,
    hop           INTEGER      NOT NULL,
    path          VARCHAR(512),
    target        VARCHAR(512),
    duration_ms   BIGINT,
    status        INTEGER      NOT NULL DEFAULT 0,
    req_time      TIMESTAMPTZ,
    res_time      TIMESTAMPTZ,
    body_url      VARCHAR(512),
    error         TEXT,
    partition_day INTEGER      NOT NULL,
    CONSTRAINT uq_gateway_log_tx_hop UNIQUE (tx_id, hop)
);

CREATE TABLE IF NOT EXISTS gateway_log_body (
    id               BIGSERIAL PRIMARY KEY,
    gateway_log_id   BIGINT    NOT NULL REFERENCES gateway_log (id),
    request_body     TEXT,
    response_body    TEXT,
    request_headers  TEXT,
    response_headers TEXT
);

CREATE TABLE IF NOT EXISTS body_collection_policy (
    id           BIGSERIAL    PRIMARY KEY,
    path_pattern VARCHAR(512) NOT NULL UNIQUE,
    enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
