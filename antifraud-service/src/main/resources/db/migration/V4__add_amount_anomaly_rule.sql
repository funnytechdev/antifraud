-- Three standard deviations on the log scale; the customer needs ten earlier purchases first,
-- and amounts under 10 000 RUB are never flagged.
INSERT INTO rule_settings (code, enabled, score, parameters) VALUES
    ('AMOUNT_ANOMALY', TRUE, 40, '{"zScoreThreshold": 3.0, "minHistory": 10, "minAmount": 10000}');
