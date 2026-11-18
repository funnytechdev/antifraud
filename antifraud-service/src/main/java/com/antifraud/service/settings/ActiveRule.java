package com.antifraud.service.settings;

import com.antifraud.contracts.TransactionEvent;
import com.antifraud.service.rules.FraudRule;
import com.antifraud.service.rules.RuleResult;

import java.time.Instant;

/**
 * A rule together with its current settings.
 *
 * @param parameters already validated parameters of the type the rule expects
 */
public record ActiveRule(FraudRule<?> rule, boolean enabled, int score, Object parameters, Instant updatedAt) {

    public String code() {
        return rule.code();
    }

    @SuppressWarnings("unchecked")
    public RuleResult evaluate(TransactionEvent transaction) {
        // Safe: the parameters were read into rule.parametersType() when the snapshot was built.
        return ((FraudRule<Object>) rule).evaluate(transaction, parameters);
    }
}
