package com.antifraud.service.settings;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/** A row of the {@code rule_settings} table; the parameters are not yet checked against the rule. */
public record StoredRuleSettings(String code, boolean enabled, int score, JsonNode parameters, Instant updatedAt) {
}
