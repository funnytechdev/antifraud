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

    static RuleResult unavailable(String cause) {
        return new Unavailable(cause);
    }

    /** The rule has no objections. */
    enum Passed implements RuleResult {
        INSTANCE
    }

    record Fired(String reason) implements RuleResult {
    }

    /** The rule could not be evaluated, e.g. because Redis is down. */
    record Unavailable(String cause) implements RuleResult {
    }
}
