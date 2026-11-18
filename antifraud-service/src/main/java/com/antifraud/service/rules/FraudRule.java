package com.antifraud.service.rules;

import com.antifraud.contracts.TransactionEvent;

/**
 * A single check applied to every transaction.
 *
 * <p>A rule only answers whether it fired and why. How many risk points that is worth, whether the
 * rule is enabled at all and the values of its parameters come from the settings stored in the
 * database, so they can be changed without a restart.
 *
 * @param <P> type of the rule parameters; a record that validates itself in its constructor
 */
public interface FraudRule<P> {

    /** Stable code of the rule; it links the rule to its settings and to the decisions it explains. */
    String code();

    Class<P> parametersType();

    /** Checks the transaction and updates the state the rule keeps about the customer, if any. */
    RuleResult evaluate(TransactionEvent transaction, P parameters);
}
