-- 800 km/h is about the cruising speed of a passenger plane; door to door a trip is slower still.
-- Purchases less than 300 km apart are ignored: merchant coordinates are approximate.
INSERT INTO rule_settings (code, enabled, score, parameters) VALUES
    ('IMPOSSIBLE_TRAVEL', TRUE, 60, '{"maxSpeedKmh": 800, "minDistanceKm": 300}');
