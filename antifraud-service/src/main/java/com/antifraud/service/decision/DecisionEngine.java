package com.antifraud.service.decision;

import com.antifraud.contracts.DecisionEvent;
import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.TransactionEvent;
import com.antifraud.contracts.Verdict;
import com.antifraud.service.rules.RuleResult;
import com.antifraud.service.rules.StateUnavailableException;
import com.antifraud.service.settings.ActiveRule;
import com.antifraud.service.settings.SettingsSnapshot;
import com.antifraud.service.settings.SettingsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs every enabled rule against a transaction and turns the accumulated risk score into a verdict.
 *
 * <p>The decision keeps the list of rules that fired, so it can always be explained. Its timestamps
 * are cut to microseconds, the precision of PostgreSQL, so a decision read back from the database is
 * identical to the one published.
 *
 * <p>If some rules cannot be evaluated because their state is unavailable, the transaction is still
 * decided by the other rules, and {@link DegradationProperties} sets the lowest verdict it may get.
 * Such a decision carries a {@value #DEGRADED} entry naming the skipped rules.
 */
@Service
public class DecisionEngine {

    /** Code of the entry that marks a decision made without some of the rules. */
    public static final String DEGRADED = "DEGRADED";

    private static final Logger log = LoggerFactory.getLogger(DecisionEngine.class);

    private final SettingsSource settings;
    private final DegradationProperties degradation;
    private final Clock clock;

    public DecisionEngine(SettingsSource settings, DegradationProperties degradation, Clock clock) {
        this.settings = settings;
        this.degradation = degradation;
        this.clock = clock;
    }

    public DecisionEvent decide(TransactionEvent transaction) {
        // One snapshot for the whole transaction, even if the settings change meanwhile.
        SettingsSnapshot snapshot = settings.current();
        List<RuleHit> hits = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        String skipCause = null;
        for (ActiveRule rule : snapshot.enabledRules()) {
            switch (evaluate(rule, transaction)) {
                case RuleResult.Fired(String reason) -> hits.add(new RuleHit(rule.code(), rule.score(), reason));
                case RuleResult.Unavailable(String cause) -> {
                    skipped.add(rule.code());
                    skipCause = cause;
                }
                case RuleResult.Passed passed -> {
                }
            }
        }

        int riskScore = hits.stream().mapToInt(RuleHit::score).sum();
        Verdict verdict = snapshot.scoring().verdictFor(riskScore);
        if (!skipped.isEmpty()) {
            Verdict floor = degradation.floorFor(transaction.amount());
            hits.add(new RuleHit(DEGRADED, 0, String.format(Locale.ROOT,
                    "Not evaluated: %s (%s); fallback verdict for this amount: %s",
                    String.join(", ", skipped), skipCause, floor)));
            verdict = max(verdict, floor);
        }
        return new DecisionEvent(
                transaction.transactionId(),
                transaction.cardId(),
                verdict,
                riskScore,
                hits,
                transaction.timestamp().truncatedTo(ChronoUnit.MICROS),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    private static RuleResult evaluate(ActiveRule rule, TransactionEvent transaction) {
        try {
            return rule.evaluate(transaction);
        } catch (StateUnavailableException e) {
            log.debug("Rule {} skipped for transaction {}: {}", rule.code(), transaction.transactionId(), e.getMessage());
            return RuleResult.unavailable(e.getMessage());
        }
    }

    /** Verdicts are declared from the mildest to the strictest. */
    private static Verdict max(Verdict a, Verdict b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
