-- Money transfers (4829), jewelry (5944), quasi-cash (6051) and gambling (7995)
-- from 20 000 RUB between midnight and 6 a.m. local time.
INSERT INTO rule_settings (code, enabled, score, parameters) VALUES
    ('RISKY_CATEGORY_AT_NIGHT', TRUE, 40,
     '{"mccs": ["4829", "5944", "6051", "7995"], "minAmount": 20000, "nightStartHour": 0, "nightEndHour": 6}');
