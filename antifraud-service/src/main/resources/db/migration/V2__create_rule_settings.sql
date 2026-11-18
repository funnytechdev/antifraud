-- Rule settings and verdict thresholds live in the database, so they can be changed through the API
-- while the service is running. Every instance re-reads them periodically.
CREATE TABLE rule_settings (
    code        VARCHAR(64) PRIMARY KEY,
    enabled     BOOLEAN     NOT NULL,
    -- Risk points the rule adds to the score when it fires.
    score       INTEGER     NOT NULL CHECK (score BETWEEN 0 AND 1000),
    -- Rule-specific parameters; their shape is validated by the service.
    parameters  JSONB       NOT NULL DEFAULT '{}',
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A single row: the "id" column can only be TRUE.
CREATE TABLE scoring_settings (
    id               BOOLEAN     PRIMARY KEY DEFAULT TRUE CHECK (id),
    review_threshold INTEGER     NOT NULL CHECK (review_threshold > 0),
    block_threshold  INTEGER     NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (review_threshold <= block_threshold)
);

INSERT INTO scoring_settings (review_threshold, block_threshold) VALUES (40, 80);

INSERT INTO rule_settings (code, enabled, score, parameters) VALUES
    ('VELOCITY',             TRUE,  50, '{"threshold": 10, "windowSeconds": 120}'),
    ('BLACKLISTED_CARD',     TRUE, 100, '{}'),
    ('BLACKLISTED_MERCHANT', TRUE,  60, '{}');
