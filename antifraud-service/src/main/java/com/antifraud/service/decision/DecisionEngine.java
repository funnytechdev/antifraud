package com.antifraud.service.decision;

import com.antifraud.contracts.DecisionEvent;
import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.TransactionEvent;
import com.antifraud.service.rules.FraudRule;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every rule against a transaction and turns the accumulated risk score into a verdict.
 *
 * <p>The decision keeps the list of rules that fired, so it can always be explained.
 */
@Service
public class DecisionEngine {

    private final List<FraudRule> rules;
    private final ScoringProperties scoring;
    private final Clock clock;

    public DecisionEngine(List<FraudRule> rules, ScoringProperties scoring, Clock clock) {
        this.rules = List.copyOf(rules);
        this.scoring = scoring;
        this.clock = clock;
    }

    public DecisionEvent decide(TransactionEvent transaction) {
        List<RuleHit> hits = new ArrayList<>();
        for (FraudRule rule : rules) {
            rule.evaluate(transaction).ifPresent(hits::add);
        }
        int riskScore = hits.stream().mapToInt(RuleHit::score).sum();
        return new DecisionEvent(
                transaction.transactionId(),
                transaction.cardId(),
                scoring.verdictFor(riskScore),
                riskScore,
                hits,
                transaction.timestamp(),
                clock.instant());
    }
}
