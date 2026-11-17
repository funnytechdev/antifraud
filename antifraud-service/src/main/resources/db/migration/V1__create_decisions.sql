-- One row per scored transaction. The primary key on transaction_id makes the insert idempotent:
-- a transaction redelivered by Kafka cannot produce a second decision.
CREATE TABLE decisions (
    transaction_id  UUID           PRIMARY KEY,
    card_id         VARCHAR(64)    NOT NULL,
    customer_id     VARCHAR(64)    NOT NULL,
    merchant_id     VARCHAR(64)    NOT NULL,
    mcc             VARCHAR(4)     NOT NULL,
    amount          NUMERIC(19, 2) NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    verdict         VARCHAR(16)    NOT NULL CHECK (verdict IN ('ALLOW', 'REVIEW', 'BLOCK')),
    risk_score      INTEGER        NOT NULL,
    -- Rules that fired, with their scores and reasons: [{"rule": "...", "score": 50, "reason": "..."}]
    triggered_rules JSONB          NOT NULL,
    event_time      TIMESTAMPTZ    NOT NULL,
    decided_at      TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_decisions_card_event_time ON decisions (card_id, event_time DESC);
CREATE INDEX idx_decisions_decided_at ON decisions (decided_at DESC);
