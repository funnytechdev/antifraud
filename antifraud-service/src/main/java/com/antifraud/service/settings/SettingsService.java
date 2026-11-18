package com.antifraud.service.settings;

import com.antifraud.service.rules.FraudRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keeps the current settings in memory and swaps them atomically when they change.
 *
 * <p>The scoring path only reads a volatile reference, so it never waits for the database. An
 * update made through this instance is applied at once; updates made through other instances
 * arrive with the periodic refresh.
 */
@Service
public class SettingsService implements SettingsSource {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final SettingsRepository repository;
    private final Map<String, FraudRule<?>> rules;
    private final JsonMapper json;
    private volatile SettingsSnapshot current;

    public SettingsService(SettingsRepository repository, List<FraudRule<?>> rules, JsonMapper json) {
        this.repository = repository;
        this.rules = rules.stream().collect(Collectors.toMap(FraudRule::code, Function.identity()));
        this.json = json.rebuild()
                // A misspelt or missing parameter must be an error, not a silent default.
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        // Fails the startup if the stored settings are broken: better than scoring without rules.
        this.current = load();
    }

    @Override
    public SettingsSnapshot current() {
        return current;
    }

    /** Picks up changes made through other instances of the service. */
    @Scheduled(fixedDelayString = "${antifraud.settings.refresh-interval}",
            initialDelayString = "${antifraud.settings.refresh-interval}")
    public void refresh() {
        try {
            current = load();
        } catch (RuntimeException e) {
            log.error("Could not reload settings, keeping the previous ones", e);
        }
    }

    public ActiveRule updateRule(String code, boolean enabled, int score, JsonNode parameters) {
        FraudRule<?> rule = rules.get(code);
        if (rule == null) {
            throw new UnknownRuleException(code);
        }
        if (score < 0 || score > 1000) {
            throw new InvalidSettingsException("score must be between 0 and 1000");
        }
        Object typed = readParameters(rule, parameters);
        if (!repository.updateRule(code, enabled, score, json.valueToTree(typed))) {
            throw new UnknownRuleException(code);
        }
        log.info("Rule {} updated: enabled={}, score={}, parameters={}", code, enabled, score, typed);
        current = load();
        return current.rule(code).orElseThrow();
    }

    public ScoringSettings updateScoring(int reviewThreshold, int blockThreshold) {
        ScoringSettings.validate(reviewThreshold, blockThreshold);
        repository.updateScoring(reviewThreshold, blockThreshold);
        log.info("Thresholds updated: review from {}, block from {}", reviewThreshold, blockThreshold);
        current = load();
        return current.scoring();
    }

    private SettingsSnapshot load() {
        Map<String, StoredRuleSettings> stored = repository.findAllRules().stream()
                .collect(Collectors.toMap(StoredRuleSettings::code, Function.identity()));

        List<ActiveRule> active = new ArrayList<>();
        for (FraudRule<?> rule : rules.values()) {
            StoredRuleSettings settings = stored.get(rule.code());
            if (settings == null) {
                throw new IllegalStateException("No settings for rule " + rule.code() + " in table rule_settings");
            }
            active.add(new ActiveRule(rule, settings.enabled(), settings.score(),
                    readParameters(rule, settings.parameters()), settings.updatedAt()));
        }
        stored.keySet().stream()
                .filter(code -> !rules.containsKey(code))
                .forEach(code -> log.warn("Settings for unknown rule {} are ignored", code));

        active.sort(Comparator.comparing(ActiveRule::code));
        return new SettingsSnapshot(repository.findScoring(), active);
    }

    private Object readParameters(FraudRule<?> rule, JsonNode parameters) {
        try {
            return json.readerFor(rule.parametersType()).readValue(parameters);
        } catch (JacksonException e) {
            throw new InvalidSettingsException("Invalid parameters of rule %s: %s"
                    .formatted(rule.code(), rootMessage(e)));
        }
    }

    /** The validation message from the parameters constructor, without Jackson's wrapping. */
    private static String rootMessage(JacksonException e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof IllegalArgumentException ? cause.getMessage() : e.getOriginalMessage();
    }
}
