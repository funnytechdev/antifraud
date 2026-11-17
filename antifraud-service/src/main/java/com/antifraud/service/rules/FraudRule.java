package com.antifraud.service.rules;

import com.antifraud.contracts.RuleHit;
import com.antifraud.contracts.TransactionEvent;

import java.util.Optional;

/** A single check applied to every transaction. */
public interface FraudRule {

    /** Stable code of the rule; it is stored with every decision the rule contributed to. */
    String code();

    /**
     * Checks the transaction and updates the state the rule keeps about the customer, if any.
     *
     * @return the hit with risk points and an explanation, or empty if the rule did not fire
     */
    Optional<RuleHit> evaluate(TransactionEvent transaction);
}
