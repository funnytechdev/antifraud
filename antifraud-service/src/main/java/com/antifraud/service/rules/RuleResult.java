package com.antifraud.service.rules;

/** Outcome of one rule for one transaction. */
public sealed interface RuleResult {

    static RuleResult passed() {
        return Passed.INSTANCE;
    }

    /**
     * @param reason explanation shown to an analyst and, in the end, to the customer
     */
    static RuleResult fired(String reason) {
        return new Fired(reason);
    }

    /** The rule has no objections. */
    enum Passed implements RuleResult {
        INSTANCE
    }

    record Fired(String reason) implements RuleResult {
    }
}
