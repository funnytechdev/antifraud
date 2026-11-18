package com.antifraud.service.settings;

import java.util.List;
import java.util.Optional;

/**
 * An immutable view of all settings at one moment.
 *
 * <p>A transaction is scored against one snapshot from start to end, so an update that arrives in
 * the middle cannot mix old thresholds with new scores.
 *
 * @param rules every known rule, enabled or not, in a stable order
 */
public record SettingsSnapshot(ScoringSettings scoring, List<ActiveRule> rules) {

    public SettingsSnapshot {
        rules = List.copyOf(rules);
    }

    public List<ActiveRule> enabledRules() {
        return rules.stream().filter(ActiveRule::enabled).toList();
    }

    public Optional<ActiveRule> rule(String code) {
        return rules.stream().filter(rule -> rule.code().equals(code)).findFirst();
    }
}
