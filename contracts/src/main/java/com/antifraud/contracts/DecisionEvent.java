package com.antifraud.contracts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Result of scoring a transaction, published to the {@code decisions} topic.
 *
 * @param riskScore      sum of the scores of all triggered rules
 * @param triggeredRules rules that fired; empty for a clean transaction
 * @param eventTime      time of the purchase, copied from the transaction
 * @param decidedAt      time the decision was made
 */
public record DecisionEvent(
        UUID transactionId,
        String cardId,
        Verdict verdict,
        int riskScore,
        List<RuleHit> triggeredRules,
        Instant eventTime,
        Instant decidedAt) {

    public DecisionEvent {
        triggeredRules = List.copyOf(triggeredRules);
    }
}
