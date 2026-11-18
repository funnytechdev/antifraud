package com.antifraud.service.settings;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/**
 * Management of rules and thresholds. A change is applied to the next transaction, without a
 * restart; other instances pick it up within the refresh interval.
 */
@RestController
@RequestMapping("/api/v1")
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/rules")
    public List<RuleView> rules() {
        return settings.current().rules().stream().map(RuleView::of).toList();
    }

    @GetMapping("/rules/{code}")
    public RuleView rule(@PathVariable String code) {
        return settings.current().rule(code).map(RuleView::of).orElseThrow(() -> new UnknownRuleException(code));
    }

    /** Replaces the settings of a rule; all fields are required. */
    @PutMapping("/rules/{code}")
    public RuleView updateRule(@PathVariable String code, @RequestBody RuleUpdate update) {
        if (update.enabled() == null || update.score() == null || update.parameters() == null) {
            throw new InvalidSettingsException("enabled, score and parameters are required");
        }
        return RuleView.of(settings.updateRule(code, update.enabled(), update.score(), update.parameters()));
    }

    @GetMapping("/scoring")
    public ScoringSettings scoring() {
        return settings.current().scoring();
    }

    @PutMapping("/scoring")
    public ScoringSettings updateScoring(@RequestBody ScoringUpdate update) {
        if (update.reviewThreshold() == null || update.blockThreshold() == null) {
            throw new InvalidSettingsException("reviewThreshold and blockThreshold are required");
        }
        return settings.updateScoring(update.reviewThreshold(), update.blockThreshold());
    }

    public record RuleView(String code, boolean enabled, int score, Object parameters, Instant updatedAt) {

        static RuleView of(ActiveRule rule) {
            return new RuleView(rule.code(), rule.enabled(), rule.score(), rule.parameters(), rule.updatedAt());
        }
    }

    public record RuleUpdate(Boolean enabled, Integer score, JsonNode parameters) {
    }

    public record ScoringUpdate(Integer reviewThreshold, Integer blockThreshold) {
    }
}
