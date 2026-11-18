package com.antifraud.service.settings;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class SettingsRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public SettingsRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<StoredRuleSettings> findAllRules() {
        return jdbc.sql("SELECT code, enabled, score, parameters, updated_at FROM rule_settings ORDER BY code")
                .query((rs, rowNum) -> new StoredRuleSettings(
                        rs.getString("code"),
                        rs.getBoolean("enabled"),
                        rs.getInt("score"),
                        json.readTree(rs.getString("parameters")),
                        rs.getObject("updated_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    /**
     * @return {@code false} if there are no settings for the rule
     */
    public boolean updateRule(String code, boolean enabled, int score, JsonNode parameters) {
        return jdbc.sql("""
                        UPDATE rule_settings
                        SET enabled = :enabled, score = :score, parameters = CAST(:parameters AS jsonb), updated_at = now()
                        WHERE code = :code
                        """)
                .param("code", code)
                .param("enabled", enabled)
                .param("score", score)
                .param("parameters", json.writeValueAsString(parameters))
                .update() == 1;
    }

    public ScoringSettings findScoring() {
        return jdbc.sql("SELECT review_threshold, block_threshold, updated_at FROM scoring_settings")
                .query((rs, rowNum) -> new ScoringSettings(
                        rs.getInt("review_threshold"),
                        rs.getInt("block_threshold"),
                        rs.getObject("updated_at", OffsetDateTime.class).toInstant()))
                .single();
    }

    public void updateScoring(int reviewThreshold, int blockThreshold) {
        jdbc.sql("""
                        UPDATE scoring_settings
                        SET review_threshold = :review, block_threshold = :block, updated_at = now()
                        """)
                .param("review", reviewThreshold)
                .param("block", blockThreshold)
                .update();
    }
}
