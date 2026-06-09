-- V4__ai_seed_content.sql
-- Flyway DDL for the AI seed content table.
-- Stores authored AI fixture content keyed by (type, subject_id).
-- Types: EXPLAIN_POSITION (per ticker), DAILY_COMMENTARY (per persona key).
-- Subject IDs: ticker symbol (e.g. 'AAPL') or persona key (e.g. 'GROWTH').
-- Seeded by AiSeedRunner @Order(2) on first startup; idempotency guarded by seed_log 'ai-v1'.

CREATE TABLE IF NOT EXISTS ai_seed_content (
    id         BIGSERIAL    PRIMARY KEY,
    type       VARCHAR(64)  NOT NULL,
    subject_id VARCHAR(32)  NOT NULL,
    content    TEXT         NOT NULL,
    CONSTRAINT uq_ai_seed UNIQUE (type, subject_id)
);
