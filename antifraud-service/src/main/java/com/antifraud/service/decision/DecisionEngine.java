package com.antifraud.service.decision;

import com.antifraud.contracts.DecisionEvent;
import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.TransactionEvent;
import com.antifraud.service.rules.RuleResult;
import com.antifraud.service.settings.ActiveRule;
import com.antifraud.service.settings.SettingsSnapshot;
import com.antifraud.service.settings.SettingsSource;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every enabled rule against a transaction and turns the accumulated risk score into a verdict.
 *
 * <p>The decision keeps the list of rules that fired, so it can always be explained. Its timestamps
 * are cut to microseconds, the precision of PostgreSQL, so a decision read back from the database is
 * identical to the one published.
 */
@Service
public class DecisionEngine {

    private final SettingsSource settings;
    private final Clock clock;

    public DecisionEngine(SettingsSource settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    public DecisionEvent decide(TransactionEvent transaction) {
        // One snapshot for the whole transaction, even if the settings change meanwhile.
        SettingsSnapshot snapshot = settings.current();
        List<RuleHit> hits = new ArrayList<>();
        for (ActiveRule rule : snapshot.enabledRules()) {
            if (rule.evaluate(transaction) instanceof RuleResult.Fired(String reason)) {
                hits.add(new RuleHit(rule.code(), rule.score(), reason));
            }
        }
        int riskScore = hits.stream().mapToInt(RuleHit::score).sum();
        return new DecisionEvent(
                transaction.transactionId(),
                transaction.cardId(),
                snapshot.scoring().verdictFor(riskScore),
                riskScore,
                hits,
                transaction.timestamp().truncatedTo(ChronoUnit.MICROS),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
