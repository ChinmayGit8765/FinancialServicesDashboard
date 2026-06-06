-- V1__schema.sql
-- Flyway-owned DDL for all relational tables + vector_store.
-- Runs as the quantlens app user AFTER docker/db/00-init.sql has
-- already installed the vector and uuid-ossp extensions as superuser.
-- SCHEMA OWNERSHIP: Flyway V1 is the sole owner of vector_store.
-- initialize-schema MUST stay false in all profiles — adding the Spring AI
-- pgvector starter in Phase 7 must NOT create a second vector_store table.

-- Securities master (equities universe + benchmark pseudo-security)
CREATE TABLE IF NOT EXISTS securities (
    id           BIGSERIAL PRIMARY KEY,
    ticker       VARCHAR(10)  NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    sector       VARCHAR(50)  NOT NULL,
    is_benchmark BOOLEAN      NOT NULL DEFAULT FALSE
);

-- OHLCV price bars (one row per security per trading day)
CREATE TABLE IF NOT EXISTS ohlcv_bars (
    id          BIGSERIAL PRIMARY KEY,
    security_id BIGINT        NOT NULL REFERENCES securities(id),
    bar_date    DATE          NOT NULL,
    open_price  NUMERIC(18,6) NOT NULL,
    high_price  NUMERIC(18,6) NOT NULL,
    low_price   NUMERIC(18,6) NOT NULL,
    close_price NUMERIC(18,6) NOT NULL,
    volume      BIGINT        NOT NULL,
    UNIQUE (security_id, bar_date)
);
CREATE INDEX IF NOT EXISTS idx_ohlcv_security_date
    ON ohlcv_bars (security_id, bar_date DESC);

-- Fama-French 3-factor daily return series (synthetic but plausible)
CREATE TABLE IF NOT EXISTS factor_returns (
    id          BIGSERIAL    PRIMARY KEY,
    factor_date DATE         NOT NULL,
    mkt_rf      NUMERIC(10,6) NOT NULL,
    smb         NUMERIC(10,6) NOT NULL,
    hml         NUMERIC(10,6) NOT NULL,
    rf          NUMERIC(10,6) NOT NULL DEFAULT 0,
    UNIQUE (factor_date)
);

-- Demo users (alice/bob/charlie with BCrypt-hashed demo1234)
CREATE TABLE IF NOT EXISTS app_users (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    persona       VARCHAR(30)  NOT NULL,
    external_id   VARCHAR(255)          -- nullable; reserved for future OAuth sub claim
);

-- Portfolios (one per demo persona)
CREATE TABLE IF NOT EXISTS portfolios (
    id      BIGSERIAL    PRIMARY KEY,
    user_id BIGINT       NOT NULL REFERENCES app_users(id),
    name    VARCHAR(100) NOT NULL,
    style   VARCHAR(30)  NOT NULL
);

-- Portfolio positions (current holdings)
CREATE TABLE IF NOT EXISTS positions (
    id             BIGSERIAL     PRIMARY KEY,
    portfolio_id   BIGINT        NOT NULL REFERENCES portfolios(id),
    security_id    BIGINT        NOT NULL REFERENCES securities(id),
    quantity       NUMERIC(18,4) NOT NULL,
    avg_cost_basis NUMERIC(18,6) NOT NULL,
    UNIQUE (portfolio_id, security_id)
);

-- Transaction history
CREATE TABLE IF NOT EXISTS transactions (
    id           BIGSERIAL     PRIMARY KEY,
    portfolio_id BIGINT        NOT NULL REFERENCES portfolios(id),
    security_id  BIGINT        NOT NULL REFERENCES securities(id),
    tx_date      DATE          NOT NULL,
    tx_type      VARCHAR(10)   NOT NULL CHECK (tx_type IN ('BUY', 'SELL')),
    quantity     NUMERIC(18,4) NOT NULL,
    price        NUMERIC(18,6) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_tx_portfolio_date
    ON transactions (portfolio_id, tx_date DESC);

-- Seed log — idempotence guard for SeedRunner
-- SeedRunner sets completed=true at the END of its @Transactional run.
-- On restart, it checks this flag and no-ops if true.
CREATE TABLE IF NOT EXISTS seed_log (
    id           VARCHAR(50) PRIMARY KEY,
    completed    BOOLEAN     NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP
);

-- vector_store — owned by Flyway (not Spring AI initialize-schema).
-- Embedding dimension LOCKED at 1536 (OpenAI text-embedding-3-small).
-- HNSW index for cosine-distance ANN search.
-- MUST stay false in application.yml/application-test.yml:
--   spring.ai.vectorstore.pgvector.initialize-schema: false
-- Leaving it true when the pgvector starter is added in Phase 7 would
-- cause Spring AI to run a second CREATE TABLE IF NOT EXISTS with its own
-- schema and index name, diverging from this Flyway-managed table.
CREATE TABLE IF NOT EXISTS vector_store (
    id        uuid    DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   text,
    metadata  json,
    embedding vector(1536)
);
CREATE INDEX IF NOT EXISTS spring_ai_vector_index
    ON vector_store USING hnsw (embedding vector_cosine_ops);
